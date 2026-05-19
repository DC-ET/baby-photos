package com.babyphotos.archive.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babyphotos.archive.domain.model.BabyAlbumMedia
import com.babyphotos.archive.util.VideoThumbnailUtils

@Composable
fun AlbumGridThumbnail(
    media: BabyAlbumMedia,
    modifier: Modifier = Modifier,
    coilSizePx: Int = 240
) {
    val context = LocalContext.current

    Box(modifier = modifier.aspectRatio(1f)) {
        if (!media.isVideo) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(media.contentUri)
                    .crossfade(true)
                    .size(coilSizePx)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            var bitmap by remember(media.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(media.id) {
                val mediaStoreId = media.id.substringAfter('_').toLongOrNull()
                bitmap = VideoThumbnailUtils.loadThumbnail(
                    context,
                    media.path ?: media.contentUri.toString(),
                    mediaStoreId,
                    coilSizePx
                )
            }
            when (val bmp = bitmap) {
                null -> Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "视频",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "视频缩略图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp),
                tint = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}
