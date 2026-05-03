package com.babyphotos.archive.domain.album

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.babyphotos.archive.domain.model.MediaType
import com.babyphotos.archive.domain.model.ScannedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AlbumManager(private val context: Context) {

    companion object {
        const val BABY_ALBUM_DIR = "Pictures/BabyAlbum"
        const val BABY_VIDEO_ALBUM_DIR = "Movies/BabyAlbum"
    }

    suspend fun moveToBabyAlbum(photo: ScannedPhoto): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    Environment.isExternalStorageManager()
                ) {
                    return@runCatching moveWithFileSystem(photo)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return@runCatching moveWithMediaStore(photo)
                }

                moveWithFileSystem(photo)
            }
        }

    suspend fun moveToBabyAlbum(
        path: String,
        mediaStoreId: Long?,
        mimeType: String = "image/jpeg",
        mediaType: MediaType = MediaType.IMAGE
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedId = mediaStoreId ?: resolveMediaStoreIdByPath(path, mediaType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    check(resolvedId != null) { "MediaStore id not found for $path" }
                }

                val photo = ScannedPhoto(
                    id = resolvedId ?: -1L,
                    path = path,
                    dateAdded = 0L,
                    mimeType = mimeType,
                    size = 0L,
                    mediaType = mediaType
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        Environment.isExternalStorageManager()
                    ) {
                        moveWithFileSystem(photo)
                    } else {
                        moveWithMediaStore(photo)
                    }
                } else {
                    moveWithFileSystem(photo)
                }
            }
        }

    private fun moveWithMediaStore(photo: ScannedPhoto): String {
        val resolver = context.contentResolver
        val sourceCollectionUri = photo.mediaType.collectionUri()
        val sourceUri = ContentUris.withAppendedId(
            sourceCollectionUri,
            photo.id
        )
        val sourceFile = File(photo.path)
        val displayName = sourceFile.name.takeIf { it.isNotBlank() }
            ?: photo.defaultDisplayName()
        val targetRelativePath = "${photo.mediaType.targetRelativePath()}/"
        val finalDisplayName = resolveUniqueDisplayName(targetRelativePath, displayName, photo.mediaType)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalDisplayName)
        }

        val updatedRows = resolver.update(sourceUri, values, null, null)
        check(updatedRows > 0) { "MediaStore move failed for ${photo.path}" }

        return queryDataPath(sourceUri)
            ?: File(Environment.getExternalStorageDirectory(), "${photo.mediaType.targetRelativePath()}/$finalDisplayName").absolutePath
    }

    private fun resolveUniqueDisplayName(
        relativePath: String,
        displayName: String,
        mediaType: MediaType
    ): String {
        var candidate = displayName
        var counter = 1
        while (existsInMediaStore(relativePath, candidate, mediaType)) {
            candidate = buildIndexedDisplayName(displayName, counter)
            counter++
        }
        return candidate
    }

    private fun existsInMediaStore(
        relativePath: String,
        displayName: String,
        mediaType: MediaType
    ): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(relativePath, displayName)

        return context.contentResolver.query(
            mediaType.collectionUri(),
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }

    private fun buildIndexedDisplayName(displayName: String, index: Int): String {
        val dotIndex = displayName.lastIndexOf('.')
        return if (dotIndex > 0) {
            val name = displayName.substring(0, dotIndex)
            val ext = displayName.substring(dotIndex)
            "${name}_$index$ext"
        } else {
            "${displayName}_$index"
        }
    }

    private fun queryDataPath(uri: android.net.Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
            } else {
                null
            }
        }
    }

    private suspend fun resolveMediaStoreIdByPath(path: String, mediaType: MediaType): Long? =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(path)

            context.contentResolver.query(
                mediaType.collectionUri(),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                } else {
                    null
                }
            }
        }

    private fun moveWithFileSystem(photo: ScannedPhoto): String {
        val sourceFile = File(photo.path)
        check(sourceFile.exists()) { "Source file not found: ${photo.path}" }

        val albumDir = File(Environment.getExternalStorageDirectory(), photo.mediaType.targetRelativePath())
        if (!albumDir.exists()) {
            check(albumDir.mkdirs()) { "Failed to create baby album directory: ${albumDir.absolutePath}" }
        }

        val destFile = File(albumDir, sourceFile.name)

        // Handle duplicate filenames
        val finalDest = if (destFile.exists()) {
            val name = sourceFile.nameWithoutExtension
            val ext = sourceFile.extension
            var counter = 1
            var candidate: File
            do {
                candidate = File(albumDir, "${name}_${counter}.${ext}")
                counter++
            } while (candidate.exists())
            candidate
        } else {
            destFile
        }

        val moved = sourceFile.renameTo(finalDest)

        // Fallback for legacy devices. If deleting the source fails, remove the copied file
        // and fail the operation so the gallery never keeps two copies silently.
        if (!moved) {
            sourceFile.copyTo(finalDest)
            if (!sourceFile.delete()) {
                finalDest.delete()
                error("Failed to delete source after copying: ${sourceFile.absolutePath}")
            }
        }

        // Sync with MediaStore
        MediaScannerConnection.scanFile(
            context,
            arrayOf(finalDest.absolutePath, sourceFile.absolutePath),
            arrayOf(photo.mimeType, photo.mimeType),
            null
        )

        return finalDest.absolutePath
    }

    fun getBabyAlbumPath(): String =
        File(Environment.getExternalStorageDirectory(), BABY_ALBUM_DIR).absolutePath

    private fun MediaType.collectionUri(): android.net.Uri =
        when (this) {
            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun MediaType.targetRelativePath(): String =
        when (this) {
            MediaType.IMAGE -> BABY_ALBUM_DIR
            MediaType.VIDEO -> BABY_VIDEO_ALBUM_DIR
        }

    private fun ScannedPhoto.defaultDisplayName(): String =
        when (mediaType) {
            MediaType.IMAGE -> "baby_photo_$id.jpg"
            MediaType.VIDEO -> "baby_video_$id.mp4"
        }
}
