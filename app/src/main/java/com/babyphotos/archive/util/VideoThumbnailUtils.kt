package com.babyphotos.archive.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoThumbnailUtils {

    suspend fun loadThumbnail(
        context: Context,
        videoPath: String,
        mediaStoreId: Long?,
        maxSize: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        loadThumbnailBlocking(context, videoPath, mediaStoreId, maxSize)
    }

    private fun loadThumbnailBlocking(
        context: Context,
        videoPath: String,
        mediaStoreId: Long?,
        maxSize: Int
    ): Bitmap? {
        val capped = maxSize.coerceIn(64, 1024)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            mediaStoreId != null &&
            mediaStoreId > 0
        ) {
            val fromStore = runCatching {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    mediaStoreId
                )
                context.contentResolver.loadThumbnail(uri, Size(capped, capped), null)
            }.getOrNull()
            if (fromStore != null) return fromStore
        }

        val file = File(videoPath)
        if (!file.exists() || !file.canRead()) return null

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(capped, capped), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(
                    videoPath,
                    MediaStore.Video.Thumbnails.MINI_KIND
                )
            }
        }.getOrNull()
    }
}
