package com.babyphotos.archive.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.babyphotos.archive.util.SettingsManager
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("设置") })

        Column(modifier = Modifier.padding(16.dp)) {
            // API Configuration
            Text("API 配置", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.apiBaseUrl,
                onValueChange = viewModel::updateApiBaseUrl,
                label = { Text("API 地址") },
                placeholder = { Text("https://api.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.modelName,
                onValueChange = viewModel::updateModelName,
                label = { Text("模型名称") },
                placeholder = { Text("gpt-4o-mini") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Test API Connection
            Button(
                onClick = viewModel::testApiConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isTestingApi
            ) {
                if (uiState.isTestingApi) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("测试中...")
                } else {
                    Text("测试 API 连接")
                }
            }

            uiState.apiTestResult?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                val (text, color) = when (result) {
                    is ApiTestResult.Success -> "连接成功，API 可正常使用" to MaterialTheme.colorScheme.primary
                    is ApiTestResult.Failure -> result.message to MaterialTheme.colorScheme.error
                }
                Text(
                    text = text,
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scan Settings
            Text("扫描设置", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text("自动添加阈值: ${uiState.autoAddThreshold}%")
            Slider(
                value = uiState.autoAddThreshold.toFloat(),
                onValueChange = { viewModel.updateAutoAddThreshold(it.toInt()) },
                valueRange = 50f..100f,
                modifier = Modifier.fillMaxWidth()
            )

            Text("并发数: ${uiState.concurrencyLimit}")
            Slider(
                value = uiState.concurrencyLimit.toFloat(),
                onValueChange = { viewModel.updateConcurrencyLimit(it.toInt()) },
                valueRange = 1f..50f,
                modifier = Modifier.fillMaxWidth()
            )

            Text("确认阈值: ${uiState.confirmThreshold}%")
            Slider(
                value = uiState.confirmThreshold.toFloat(),
                onValueChange = { viewModel.updateConfirmThreshold(it.toInt()) },
                valueRange = 20f..79f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scan Start Date
            Text("扫描起始时间", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "仅扫描此日期之后添加的照片，避免处理历史照片",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            val dateText = if (uiState.scanStartDate > 0L) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.format(Date(uiState.scanStartDate * 1000))
            } else {
                "未设置（扫描所有照片）"
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(dateText)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recognition Prompts
            Text("识别提示词", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "自定义 AI 识别图片/视频时使用的提示词。留空则使用默认值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.systemPrompt,
                onValueChange = viewModel::updateSystemPrompt,
                label = { Text("System 提示词") },
                placeholder = { Text(SettingsManager.DEFAULT_SYSTEM_PROMPT) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.userPrompt,
                onValueChange = viewModel::updateUserPrompt,
                label = { Text("User 提示词") },
                placeholder = { Text(SettingsManager.DEFAULT_USER_PROMPT) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Image Preprocessing
            Text("图片预处理", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.maxImageSize.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> viewModel.updateMaxImageSize(v) } },
                label = { Text("最大尺寸 (px)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("JPEG 质量: ${uiState.jpegQuality}%")
            Slider(
                value = uiState.jpegQuality.toFloat(),
                onValueChange = { viewModel.updateJpegQuality(it.toInt()) },
                valueRange = 50f..90f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isSaved) "已保存" else "保存设置")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (uiState.scanStartDate > 0L) {
                uiState.scanStartDate * 1000
            } else {
                System.currentTimeMillis()
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // Convert to epoch seconds (start of day in local timezone)
                            val epochSeconds = millis / 1000
                            viewModel.updateScanStartDate(epochSeconds)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
