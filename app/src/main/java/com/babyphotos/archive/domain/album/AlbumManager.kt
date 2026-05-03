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

    /**
     * 将已归档到宝宝相册的媒体移回 [originalPath] 所在目录（文件名与 [originalPath] 一致，冲突时自动加后缀）。
     * @param archivedPath 当前磁盘路径（通常为 [ImageAnalysisEntity.movedTo]）
     * @param originalPath 扫描时的原始路径（[ImageAnalysisEntity.path]）
     */
    suspend fun moveBackFromBabyAlbum(
        archivedPath: String,
        originalPath: String,
        mediaStoreId: Long?,
        mimeType: String = "image/jpeg",
        mediaType: MediaType = MediaType.IMAGE
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedId = mediaStoreId ?: resolveMediaStoreIdByPath(archivedPath, mediaType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    check(resolvedId != null) { "MediaStore id not found for $archivedPath" }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    Environment.isExternalStorageManager()
                ) {
                    return@runCatching moveWithFileSystemBack(archivedPath, originalPath, mimeType)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return@runCatching moveWithMediaStoreBack(
                        archivedPath = archivedPath,
                        originalPath = originalPath,
                        mediaId = resolvedId!!,
                        mediaType = mediaType
                    )
                }

                moveWithFileSystemBack(archivedPath, originalPath, mimeType)
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

    private fun moveWithMediaStoreBack(
        @Suppress("UNUSED_PARAMETER") archivedPath: String,
        originalPath: String,
        mediaId: Long,
        mediaType: MediaType
    ): String {
        val (targetRelativePath, displayNameBase) = absolutePathToRelativePathAndDisplayName(originalPath)
        val finalDisplayName = resolveUniqueDisplayName(targetRelativePath, displayNameBase, mediaType)

        val resolver = context.contentResolver
        val sourceUri = ContentUris.withAppendedId(mediaType.collectionUri(), mediaId)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalDisplayName)
        }

        val updatedRows = resolver.update(sourceUri, values, null, null)
        check(updatedRows > 0) { "MediaStore restore failed for $archivedPath" }

        return queryDataPath(sourceUri)
            ?: File(
                Environment.getExternalStorageDirectory(),
                "${targetRelativePath.trimEnd('/')}/$finalDisplayName"
            ).absolutePath
    }

    private fun moveWithFileSystemBack(archivedPath: String, originalPath: String, mimeType: String): String {
        val sourceFile = File(archivedPath)
        check(sourceFile.exists()) { "Source file not found: $archivedPath" }

        val targetFile = File(originalPath)
        val destDir = targetFile.parentFile ?: error("Invalid original path: $originalPath")
        if (!destDir.exists()) {
            check(destDir.mkdirs()) { "Failed to create directory: ${destDir.absolutePath}" }
        }

        var finalDest = targetFile
        if (finalDest.exists() && finalDest.absolutePath != sourceFile.absolutePath) {
            val name = targetFile.nameWithoutExtension
            val ext = targetFile.extension
            var counter = 1
            var candidate: File
            do {
                candidate = File(destDir, "${name}_${counter}.${ext}")
                counter++
            } while (candidate.exists())
            finalDest = candidate
        }

        val moved = sourceFile.renameTo(finalDest)
        if (!moved) {
            sourceFile.copyTo(finalDest, overwrite = true)
            if (!sourceFile.delete()) {
                finalDest.delete()
                error("Failed to delete source after copying: ${sourceFile.absolutePath}")
            }
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(sourceFile.absolutePath, finalDest.absolutePath),
            arrayOf(mimeType, mimeType),
            null
        )

        return finalDest.absolutePath
    }

    private fun absolutePathToRelativePathAndDisplayName(absolutePath: String): Pair<String, String> {
        val file = File(absolutePath)
        val displayName = file.name
        val parent = file.parentFile ?: error("Invalid path: $absolutePath")
        val extRoot = Environment.getExternalStorageDirectory().absolutePath
        val parentAbs = parent.absolutePath
        check(parentAbs.startsWith(extRoot)) { "Path outside primary external storage: $absolutePath" }
        var relative = parentAbs.removePrefix(extRoot).trimStart(File.separatorChar)
        relative = relative.replace(File.separatorChar, '/')
        val relativePath = if (relative.endsWith("/")) relative else "$relative/"
        return relativePath to displayName
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
            MediaType.IMAGE, MediaType.VIDEO -> BABY_ALBUM_DIR
        }

    private fun ScannedPhoto.defaultDisplayName(): String =
        when (mediaType) {
            MediaType.IMAGE -> "baby_photo_$id.jpg"
            MediaType.VIDEO -> "baby_video_$id.mp4"
        }
}
