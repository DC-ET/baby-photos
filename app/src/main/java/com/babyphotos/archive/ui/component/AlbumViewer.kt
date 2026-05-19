package com.babyphotos.archive.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.request.ImageRequest
import com.babyphotos.archive.domain.model.BabyAlbumMedia

@Composable
fun AlbumViewer(
    items: List<BabyAlbumMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size }
    )
    var pagerScrollEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pagerScrollEnabled
        ) { page ->
            val media = items[page]
            AlbumViewerPage(
                media = media,
                onZoomChanged = { zoomed -> pagerScrollEnabled = !zoomed }
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun AlbumViewerPage(
    media: BabyAlbumMedia,
    onZoomChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current

    if (media.isVideo) {
        val player = remember(media.id) {
            ExoPlayer.Builder(context).build()
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
        LaunchedEffect(media.id) {
            player.setMediaItem(MediaItem.fromUri(media.contentUri))
            player.prepare()
            player.playWhenReady = true
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    ZoomableImage(
        model = ImageRequest.Builder(context)
            .data(media.contentUri)
            .crossfade(true)
            .build(),
        contentDescription = "相册大图",
        modifier = Modifier.fillMaxSize(),
        onZoomChanged = onZoomChanged
    )
}
