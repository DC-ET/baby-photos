package com.babyphotos.archive.ui.component

import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.model.MediaType
import com.babyphotos.archive.util.VideoThumbnailUtils
import java.io.File

@Composable
fun AnalysisMediaThumbnail(
    entity: ImageAnalysisEntity,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    coilSizePx: Int = 180,
    showVideoPlayBadge: Boolean = true
) {
    val context = LocalContext.current
    val path = entity.movedTo ?: entity.path

    if (!entity.isMediaVideo()) {
        val file = remember(path) { File(path) }
        val placeholder = painterResource(android.R.drawable.ic_menu_gallery)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .crossfade(true)
                .size(coilSizePx)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = placeholder,
            error = placeholder
        )
        return
    }

    var bitmap by remember(path, entity.mediaStoreId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, entity.mediaStoreId) {
        bitmap = VideoThumbnailUtils.loadThumbnail(context, path, entity.mediaStoreId, coilSizePx)
    }

    Box(modifier = modifier) {
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
                contentScale = contentScale
            )
        }
        if (showVideoPlayBadge && bitmap != null) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                tint = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
fun HistoryDetailMediaPreview(
    entity: ImageAnalysisEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val path = entity.movedTo ?: entity.path
    val placeholder = if (entity.isMediaVideo()) {
        painterResource(android.R.drawable.ic_media_play)
    } else {
        painterResource(android.R.drawable.ic_menu_gallery)
    }

    if (!entity.isMediaVideo()) {
        val file = remember(path) { File(path) }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = "历史照片大图",
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 360.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Fit,
            placeholder = placeholder,
            error = placeholder
        )
        return
    }

    var playing by remember(entity.id) { mutableStateOf(false) }
    val player = remember(entity.id) {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    var thumb by remember(path, entity.mediaStoreId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, entity.mediaStoreId) {
        thumb = VideoThumbnailUtils.loadThumbnail(context, path, entity.mediaStoreId, 720)
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (playing) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            )
            LaunchedEffect(playing, path, entity.mediaStoreId) {
                if (!playing) return@LaunchedEffect
                val uri = entity.resolvePlaybackUri(path)
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = true
            }
            TextButton(
                onClick = {
                    playing = false
                    player.stop()
                    player.clearMediaItems()
                }
            ) {
                Text("收起播放器")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 360.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable(
                        onClick = { playing = true },
                        role = androidx.compose.ui.semantics.Role.Button
                    )
            ) {
                when (val bmp = thumb) {
                    null -> Image(
                        painter = placeholder,
                        contentDescription = "历史视频预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                    else -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "历史视频预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.12f))
                )
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp),
                    tint = Color.White.copy(alpha = 0.95f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { playing = true }) {
                Text("播放视频")
            }
        }
    }
}

private fun ImageAnalysisEntity.resolvePlaybackUri(path: String): Uri {
    val id = mediaStoreId
    if (id != null && id > 0L) {
        return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    }
    return File(path).toUri()
}

private fun ImageAnalysisEntity.isMediaVideo(): Boolean =
    mediaType == MediaType.VIDEO.name
