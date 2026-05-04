package com.babyphotos.archive.ui.screen.history

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.model.ClassificationAction
import com.babyphotos.archive.domain.model.MediaType
import com.babyphotos.archive.ui.component.AnalysisMediaThumbnail
import com.babyphotos.archive.ui.component.ConfirmDialog
import com.babyphotos.archive.ui.component.ConfidenceBadge
import com.babyphotos.archive.ui.component.HistoryDetailMediaPreview
import com.babyphotos.archive.util.PhotoPermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryItemShape = RoundedCornerShape(12.dp)

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
    var removeConfirmEntity by remember { mutableStateOf<ImageAnalysisEntity?>(null) }
    var showCleanConfirm by remember { mutableStateOf(false) }

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

    removeConfirmEntity?.let { target ->
        ConfirmDialog(
            title = "移出宝宝相册",
            message = "将把该文件移回原始目录，并从「宝宝」记录中移除（标记为已忽略）。是否继续？",
            confirmLabel = "移出相册",
            onConfirm = {
                removeConfirmEntity = null
                viewModel.removeFromBabyAlbum(target)
            },
            onDismiss = { removeConfirmEntity = null }
        )
    }

    if (uiState.showMovePermissionDialog) {
        ConfirmDialog(
            title = "需要文件管理权限",
            message = "移动或移回照片、视频需要系统文件访问权限。请在系统设置中允许\"管理所有文件\"，授权后返回本页再操作。",
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

    if (showCleanConfirm) {
        ConfirmDialog(
            title = "清理无效记录",
            message = "将扫描所有历史记录，自动删除对应的图片/视频已从系统相册中移除的条目。此操作不可撤销，是否继续？",
            confirmLabel = "清理",
            onConfirm = {
                showCleanConfirm = false
                viewModel.cleanStaleRecords()
            },
            onDismiss = { showCleanConfirm = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        TopAppBar(
            title = { Text("历史记录") },
            actions = {
                IconButton(
                    onClick = { showCleanConfirm = true },
                    enabled = !uiState.isCleaningStale
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "清理无效记录"
                    )
                }
            }
        )

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
                        showRemoveAction = uiState.filter == HistoryFilter.BABY,
                        isMoving = uiState.movingItemIds.contains(entity.id),
                        onOpenDetail = { selectedEntity = entity },
                        onMoveToBabyAlbum = { viewModel.moveIgnoredToBabyAlbum(entity) },
                        onRemoveFromBabyAlbum = { removeConfirmEntity = entity }
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
    showRemoveAction: Boolean,
    isMoving: Boolean,
    onOpenDetail: () -> Unit,
    onMoveToBabyAlbum: () -> Unit,
    onRemoveFromBabyAlbum: () -> Unit
) {
    val hasSwipeAction = (showMoveAction && entity.action == ClassificationAction.IGNORE.name) ||
            (showRemoveAction && entity.movedTo != null)

    if (!hasSwipeAction) {
        HistoryItemCard(entity = entity, onOpenDetail = onOpenDetail)
        return
    }

    val actionColor = if (showRemoveAction) Color(0xFFE53935) else Color(0xFF4CAF50)
    val actionLabel = if (showRemoveAction) "移出相册" else "移入相册"
    val actionIcon = if (showRemoveAction) Icons.Default.Delete else Icons.Default.MoveToInbox

    val actionWidthDp = 76.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidthDp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        // Revealed action button behind the card
        Box(
            modifier = Modifier
                .matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(actionWidthDp)
                    .clip(HistoryItemShape)
                    .background(actionColor)
                    .clickable(enabled = !isMoving) {
                        if (showRemoveAction) onRemoveFromBabyAlbum() else onMoveToBabyAlbum()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = actionLabel,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isMoving && showRemoveAction) "处理中" else if (isMoving) "移动中" else actionLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Foreground card that slides
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(isMoving) {
                    if (isMoving) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (offsetX.value < -actionWidthPx / 2f) -actionWidthPx else 0f
                                offsetX.animateTo(target, tween(200))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, tween(200))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newValue = (offsetX.value + dragAmount)
                                    .coerceIn(-actionWidthPx, 0f)
                                offsetX.snapTo(newValue)
                            }
                        }
                    )
                }
        ) {
            HistoryItemCard(
                entity = entity,
                onOpenDetail = {
                    if (offsetX.value < 0f) {
                        scope.launch { offsetX.animateTo(0f, tween(200)) }
                    } else {
                        onOpenDetail()
                    }
                }
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    entity: ImageAnalysisEntity,
    onOpenDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        shape = HistoryItemShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnalysisMediaThumbnail(
                entity = entity,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                coilSizePx = 160
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
            }
        }
    }
}

@Composable
private fun HistoryDetailDialog(
    entity: ImageAnalysisEntity,
    onDismiss: () -> Unit
) {
    val imagePath = entity.movedTo ?: entity.path
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

                HistoryDetailMediaPreview(
                    entity = entity,
                    modifier = Modifier.fillMaxWidth()
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
