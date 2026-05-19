package com.babyphotos.archive.domain.album

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.babyphotos.archive.domain.model.BabyAlbumMedia
import com.babyphotos.archive.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BabyAlbumReader(private val context: Context) {

    suspend fun fetchAllMedia(): List<BabyAlbumMedia> = withContext(Dispatchers.IO) {
        val images = queryAlbumMedia(MediaType.IMAGE)
        val videos = queryAlbumMedia(MediaType.VIDEO)
        (images + videos).sortedByDescending { it.createdAtMillis }
    }

    private fun queryAlbumMedia(mediaType: MediaType): List<BabyAlbumMedia> {
        val collection = mediaType.collectionUri()
        val projection = buildProjection(mediaType)
        val (selection, selectionArgs) = albumSelection()

        return context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${createdAtColumn(mediaType)} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = if (pathCol >= 0) cursor.getString(pathCol) else null
                    val takenMs = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                    val addedSec = cursor.getLong(addedCol)
                    val createdAtMillis = when {
                        takenMs > 0L -> takenMs
                        addedSec > 0L -> addedSec * 1000L
                        else -> 0L
                    }
                    add(
                        BabyAlbumMedia(
                            id = "${mediaType.name}_$id",
                            mediaType = mediaType,
                            createdAtMillis = createdAtMillis,
                            contentUri = ContentUris.withAppendedId(collection, id),
                            path = path
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private fun albumSelection(): Pair<String, Array<String>> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to
                arrayOf("${AlbumManager.BABY_ALBUM_DIR}/%")
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/BabyAlbum/%")
        }
    }

    private fun buildProjection(@Suppress("UNUSED_PARAMETER") mediaType: MediaType): Array<String> =
        arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED
        )

    private fun createdAtColumn(@Suppress("UNUSED_PARAMETER") mediaType: MediaType): String =
        MediaStore.MediaColumns.DATE_TAKEN

    private fun MediaType.collectionUri(): android.net.Uri =
        when (this) {
            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
}
