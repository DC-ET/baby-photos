package com.babyphotos.archive.domain.scanner

import android.content.Context
import android.provider.MediaStore
import com.babyphotos.archive.domain.model.ScannedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

interface PhotoScanner {
    suspend fun scanTodayPhotos(): List<ScannedPhoto>
    suspend fun scanPhotosSince(timestamp: Long): List<ScannedPhoto>
}

class MediaStorePhotoScanner(
    private val context: Context
) : PhotoScanner {

    override suspend fun scanTodayPhotos(): List<ScannedPhoto> {
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toEpochSecond()
        return scanPhotosSince(startOfDay)
    }

    override suspend fun scanPhotosSince(timestamp: Long): List<ScannedPhoto> =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE
            )
            val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
            val selectionArgs = arrayOf(timestamp.toString())
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            ScannedPhoto(
                                id = cursor.getLong(idCol),
                                path = cursor.getString(pathCol) ?: continue,
                                dateAdded = cursor.getLong(dateCol),
                                mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                                size = cursor.getLong(sizeCol)
                            )
                        )
                    }
                }
            } ?: emptyList()
        }
}
