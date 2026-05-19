package com.babyphotos.archive.ui.screen.album

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.babyphotos.archive.domain.model.BabyAlbumDateSection
import com.babyphotos.archive.domain.model.BabyAlbumMedia
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babyphotos.archive.ui.component.AlbumGridThumbnail
import com.babyphotos.archive.ui.component.AlbumViewer
import com.babyphotos.archive.util.PhotoPermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    paddingValues: PaddingValues,
    viewModel: AlbumViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    Scaffold(
        modifier = Modifier.padding(paddingValues),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("相册")
                        if (uiState.displayState == AlbumDisplayState.Content) {
                            Text(
                                text = "共 ${uiState.flatItems.size} 项",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.displayState == AlbumDisplayState.Loading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.displayState) {
                AlbumDisplayState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                AlbumDisplayState.PermissionDenied -> {
                    PermissionDeniedContent(
                        onRequestPermission = {
                            permissionLauncher.launch(PhotoPermissionUtils.requiredReadPermissions)
                        }
                    )
                }
                AlbumDisplayState.Empty -> EmptyAlbumContent()
                AlbumDisplayState.Content -> AlbumGridContent(
                    sections = uiState.sections,
                    onItemClick = { media ->
                        val index = uiState.flatItems.indexOfFirst { it.id == media.id }
                        if (index >= 0) viewModel.openViewer(index)
                    }
                )
            }
        }
    }

    val selectedIndex = uiState.selectedIndex
    if (selectedIndex != null && uiState.flatItems.isNotEmpty()) {
        Dialog(
            onDismissRequest = { viewModel.closeViewer() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AlbumViewer(
                items = uiState.flatItems,
                initialIndex = selectedIndex,
                onDismiss = { viewModel.closeViewer() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AlbumGridContent(
    sections: List<BabyAlbumDateSection>,
    onItemClick: (BabyAlbumMedia) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sections, key = { it.id }) { section ->
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightInGrid(section.items.size),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                userScrollEnabled = false
            ) {
                items(section.items, key = { it.id }) { media ->
                    AlbumGridThumbnail(
                        media = media,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(media) }
                    )
                }
            }
        }
    }
}

private fun Modifier.heightInGrid(itemCount: Int): Modifier {
    val rows = (itemCount + 2) / 3
    val rowHeight = 120.dp
    return this.height((rowHeight * rows) + (2.dp * (rows - 1).coerceAtLeast(0)))
}

@Composable
private fun PermissionDeniedContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "需要相册读取权限才能查看宝宝相册",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("授予相册权限")
        }
    }
}

@Composable
private fun EmptyAlbumContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("宝宝相册还是空的", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "去首页扫描并归档照片后，会显示在这里",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
