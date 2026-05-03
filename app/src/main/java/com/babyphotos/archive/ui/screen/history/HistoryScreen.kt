package com.babyphotos.archive.ui.screen.history

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.model.ClassificationAction
import com.babyphotos.archive.domain.model.MediaType
import com.babyphotos.archive.ui.component.ConfirmDialog
import com.babyphotos.archive.ui.component.ConfidenceBadge
import com.babyphotos.archive.util.PhotoPermissionUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedEntity by remember { mutableStateOf<ImageAnalysisEntity?>(null) }

    uiState.userMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMovePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val movePermissionLauncher = rememberLauncherForActivityResult(
        contract = StartActivityForResult()
    ) {
        viewModel.refreshMovePermission()
    }

    if (uiState.showMovePermissionDialog) {
        ConfirmDialog(
            title = "需要文件管理权限",
            message = "移动到宝宝相册需要移动照片或视频。请在系统设置中允许“管理所有文件”，授权后返回本页再点击移动。",
            confirmLabel = "去设置",
            onConfirm = {
                viewModel.dismissMovePermissionDialog()
                val intent = PhotoPermissionUtils.createManageStorageIntent(context)
                runCatching {
                    movePermissionLauncher.launch(intent)
                }.onFailure {
                    movePermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
                }
            },
            onDismiss = viewModel::dismissMovePermissionDialog
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        TopAppBar(title = { Text("历史记录") })

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = {
                        Text(
                            when (filter) {
                                HistoryFilter.ALL -> "全部"
                                HistoryFilter.BABY -> "宝宝"
                                HistoryFilter.CONFIRMED -> "已确认"
                                HistoryFilter.IGNORED -> "已忽略"
                            }
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.filteredItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("暂无记录", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredItems, key = { it.id }) { entity ->
                    HistoryItem(
                        entity = entity,
                        showMoveAction = uiState.filter == HistoryFilter.IGNORED,
                        isMoving = uiState.movingItemIds.contains(entity.id),
                        onOpenDetail = { selectedEntity = entity },
                        onMoveToBabyAlbum = { viewModel.moveIgnoredToBabyAlbum(entity) }
                    )
                }
            }
        }
    }

    selectedEntity?.let { entity ->
        HistoryDetailDialog(
            entity = entity,
            onDismiss = { selectedEntity = null }
        )
    }
}

@Composable
private fun HistoryItem(
    entity: ImageAnalysisEntity,
    showMoveAction: Boolean,
    isMoving: Boolean,
    onOpenDetail: () -> Unit,
    onMoveToBabyAlbum: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imagePath = entity.movedTo ?: entity.path
            val imageFile = File(imagePath)
            val context = LocalContext.current
            val placeholder = if (entity.isVideo()) {
                painterResource(android.R.drawable.ic_media_play)
            } else {
                painterResource(android.R.drawable.ic_menu_gallery)
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageFile)
                    .crossfade(true)
                    .size(160)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.path.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entity.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                ConfidenceBadge(confidence = entity.confidence)
            }

            val actionLabel = entity.actionLabel()
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (entity.action) {
                        ClassificationAction.AUTO_ADD.name -> MaterialTheme.colorScheme.primary
                        ClassificationAction.NEEDS_CONFIRM.name -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (showMoveAction && entity.action == ClassificationAction.IGNORE.name) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onMoveToBabyAlbum,
                        enabled = !isMoving,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(if (isMoving) "移动中" else "移到相册")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDetailDialog(
    entity: ImageAnalysisEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imagePath = entity.movedTo ?: entity.path
    val imageFile = File(imagePath)
    val placeholder = if (entity.isVideo()) {
        painterResource(android.R.drawable.ic_media_play)
    } else {
        painterResource(android.R.drawable.ic_menu_gallery)
    }
    val analyzedAt = remember(entity.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entity.timestamp))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = entity.path.substringAfterLast("/"),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = if (entity.isVideo()) "历史视频预览" else "历史照片大图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 360.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                    placeholder = placeholder,
                    error = placeholder
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailRow(label = "状态", value = entity.actionLabel())
                DetailRow(label = "类型", value = if (entity.isVideo()) "视频" else "照片")
                DetailRow(label = "是否包含宝宝", value = if (entity.containsBaby) "是" else "否")
                DetailRow(label = "置信度", value = "${entity.confidence}%")
                DetailRow(label = "分析时间", value = analyzedAt)
                DetailRow(label = "当前路径", value = imagePath)
                if (entity.movedTo != null) {
                    DetailRow(label = "原始路径", value = entity.path)
                    DetailRow(label = "归档路径", value = entity.movedTo)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "识别描述",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entity.reason,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun ImageAnalysisEntity.actionLabel(): String {
    return when (action) {
        ClassificationAction.AUTO_ADD.name -> "已归档"
        ClassificationAction.NEEDS_CONFIRM.name -> "待确认"
        ClassificationAction.IGNORE.name -> "已忽略"
        else -> action
    }
}

private fun ImageAnalysisEntity.isVideo(): Boolean = mediaType == MediaType.VIDEO.name
