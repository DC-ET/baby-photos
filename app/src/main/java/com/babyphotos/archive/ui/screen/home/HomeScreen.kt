package com.babyphotos.archive.ui.screen.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.model.MediaType
import com.babyphotos.archive.ui.component.AnalysisMediaThumbnail
import com.babyphotos.archive.ui.component.ConfidenceBadge
import com.babyphotos.archive.util.PhotoPermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    uiState.userMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPhotoPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        viewModel.onPermissionResult(granted)
        if (granted) {
            viewModel.requestStartScan()
        }
    }

    val movePermissionLauncher = rememberLauncherForActivityResult(
        contract = StartActivityForResult()
    ) {
        viewModel.refreshPhotoPermission()
    }

    if (uiState.showMovePermissionDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMovePermissionDialog,
            title = { Text("需要文件管理权限") },
            text = { Text("添加到宝宝相册需要移动照片或视频。请在系统设置中允许“管理所有文件”，授权后返回本页再点击添加。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissMovePermissionDialog()
                        val intent = PhotoPermissionUtils.createManageStorageIntent(context)
                        runCatching {
                            movePermissionLauncher.launch(intent)
                        }.onFailure {
                            movePermissionLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            )
                        }
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMovePermissionDialog) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.showScanStartDateDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = viewModel::dismissScanStartDateDialog,
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.confirmScanStartDate(millis / 1000)
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissScanStartDateDialog) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("设置扫描起始时间") },
                headline = { Text("请选择开始扫描的日期") }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Icon(
            imageVector = Icons.Default.ChildCare,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "宝宝相册",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "AI 智能识别照片和视频，自动归档",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Scan button / progress
        if (uiState.isScanning) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("正在扫描照片和视频...", style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(
                onClick = {
                    if (PhotoPermissionUtils.hasFullReadPermission(context)) {
                        viewModel.onPermissionResult(true)
                        viewModel.requestStartScan()
                    } else {
                        permissionLauncher.launch(PhotoPermissionUtils.requiredReadPermissions)
                    }
                },
                enabled = uiState.isApiConfigured,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Text("  立即扫描")
            }

            if (!uiState.isApiConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "请先在设置中配置 API",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!uiState.hasPhotoPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "需要相册读取权限才能扫描照片和视频",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!uiState.hasMovePermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "添加照片或视频需要文件管理权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("统计", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("已归档宝宝媒体: ${uiState.babyPhotoCount}")

                uiState.lastScanSummary?.let { summary ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("上次扫描: 共${summary.totalScanned}个, 自动添加${summary.autoAdded}个, 待确认${summary.needsConfirmation}个")
                }
            }
        }

        // Pending items
        if (uiState.pendingItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "待确认照片/视频 (${uiState.pendingItems.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.rejectAll() },
                        enabled = !uiState.isConfirming
                    ) {
                        Text("全部跳过")
                    }
                    Button(
                        onClick = { viewModel.confirmAll() },
                        enabled = !uiState.isConfirming
                    ) {
                        Text("全部确认")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.pendingItems, key = { it.id }) { entity ->
                    PendingPhotoItem(
                        entity = entity,
                        onConfirm = { viewModel.confirmItem(entity) },
                        onReject = { viewModel.rejectItem(entity) },
                        enabled = !uiState.isConfirming
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingPhotoItem(
    entity: ImageAnalysisEntity,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                coilSizePx = 180
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.path.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entity.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                ConfidenceBadge(confidence = entity.confidence)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onReject, enabled = enabled) { Text("跳过") }
                Button(onClick = onConfirm, enabled = enabled) { Text("添加") }
            }
        }
    }
}

private fun ImageAnalysisEntity.isVideo(): Boolean = mediaType == MediaType.VIDEO.name
