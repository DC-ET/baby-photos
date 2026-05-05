package com.babyphotos.archive.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babyphotos.archive.BabyPhotosApp
import com.babyphotos.archive.util.SettingsManager
import com.babyphotos.archive.util.SettingsManager.Companion.DEFAULT_SYSTEM_PROMPT
import com.babyphotos.archive.util.SettingsManager.Companion.DEFAULT_USER_PROMPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SettingsUiState(
    val apiBaseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode",
    val apiKey: String = "",
    val modelName: String = "qwen3-vl-flash",
    val autoAddThreshold: Int = 80,
    val confirmThreshold: Int = 50,
    val maxImageSize: Int = 1024,
    val jpegQuality: Int = 70,
    val concurrencyLimit: Int = 10,
    val scanStartDate: Long = 0L,
    val systemPrompt: String = "",
    val userPrompt: String = "",
    val isSaved: Boolean = false,
    val isTestingApi: Boolean = false,
    val apiTestResult: ApiTestResult? = null
)

sealed class ApiTestResult {
    data object Success : ApiTestResult()
    data class Failure(val message: String) : ApiTestResult()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            apiBaseUrl = settingsManager.apiBaseUrl,
            apiKey = settingsManager.apiKey,
            modelName = settingsManager.modelName,
            autoAddThreshold = settingsManager.autoAddThreshold,
            confirmThreshold = settingsManager.confirmThreshold,
            maxImageSize = settingsManager.maxImageSize,
            jpegQuality = settingsManager.jpegQuality,
            concurrencyLimit = settingsManager.concurrencyLimit,
            scanStartDate = settingsManager.scanStartDate,
            systemPrompt = settingsManager.systemPrompt,
            userPrompt = settingsManager.userPrompt
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateApiBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(apiBaseUrl = value, isSaved = false)
    }

    fun updateApiKey(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value, isSaved = false)
    }

    fun updateModelName(value: String) {
        _uiState.value = _uiState.value.copy(modelName = value, isSaved = false)
    }

    fun updateAutoAddThreshold(value: Int) {
        _uiState.value = _uiState.value.copy(autoAddThreshold = value, isSaved = false)
    }

    fun updateConfirmThreshold(value: Int) {
        _uiState.value = _uiState.value.copy(confirmThreshold = value, isSaved = false)
    }

    fun updateMaxImageSize(value: Int) {
        _uiState.value = _uiState.value.copy(maxImageSize = value, isSaved = false)
    }

    fun updateJpegQuality(value: Int) {
        _uiState.value = _uiState.value.copy(jpegQuality = value, isSaved = false)
    }

    fun updateConcurrencyLimit(value: Int) {
        _uiState.value = _uiState.value.copy(concurrencyLimit = value, isSaved = false)
    }

    fun updateScanStartDate(value: Long) {
        _uiState.value = _uiState.value.copy(scanStartDate = value, isSaved = false)
    }

    fun updateSystemPrompt(value: String) {
        _uiState.value = _uiState.value.copy(systemPrompt = value, isSaved = false)
    }

    fun updateUserPrompt(value: String) {
        _uiState.value = _uiState.value.copy(userPrompt = value, isSaved = false)
    }

    fun saveSettings() {
        val state = _uiState.value
        settingsManager.apiBaseUrl = state.apiBaseUrl.trimEnd('/')
        settingsManager.apiKey = state.apiKey
        settingsManager.modelName = state.modelName
        settingsManager.autoAddThreshold = state.autoAddThreshold
        settingsManager.confirmThreshold = state.confirmThreshold
        settingsManager.maxImageSize = state.maxImageSize
        settingsManager.jpegQuality = state.jpegQuality
        settingsManager.concurrencyLimit = state.concurrencyLimit
        settingsManager.scanStartDate = state.scanStartDate
        val effectiveSystemPrompt = state.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }
        val effectiveUserPrompt = state.userPrompt.ifBlank { DEFAULT_USER_PROMPT }
        settingsManager.systemPrompt = effectiveSystemPrompt
        settingsManager.userPrompt = effectiveUserPrompt

        // Update the recognizer in App
        val app = getApplication<BabyPhotosApp>()
        app.updateRecognizer(
            apiBaseUrl = state.apiBaseUrl.trimEnd('/'),
            apiKey = state.apiKey,
            modelName = state.modelName,
            systemPrompt = effectiveSystemPrompt,
            userPrompt = effectiveUserPrompt
        )

        _uiState.value = _uiState.value.copy(isSaved = true)
    }

    fun testApiConnection() {
        val state = _uiState.value
        if (state.apiBaseUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(
                apiTestResult = ApiTestResult.Failure("请先填写 API 地址和 API Key")
            )
            return
        }

        _uiState.value = _uiState.value.copy(isTestingApi = true, apiTestResult = null)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()

                    val body = JSONObject().apply {
                        put("model", state.modelName.ifBlank { "qwen3-vl-flash" })
                        put("max_tokens", 100)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "你好")
                            })
                        })
                    }.toString().toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url("${state.apiBaseUrl.trimEnd('/')}/v1/chat/completions")
                        .addHeader("Authorization", "Bearer ${state.apiKey}")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        val errorMsg = when (response.code) {
                            401 -> "认证失败，请检查 API Key 是否正确"
                            403 -> "访问被拒绝，请检查 API Key 权限"
                            404 -> "接口不存在，请检查 API 地址是否正确"
                            429 -> "请求过于频繁，请稍后再试"
                            in 500..599 -> "服务器错误 (${response.code})，请稍后再试"
                            else -> "请求失败 (${response.code}): $responseBody"
                        }
                        throw IllegalStateException(errorMsg)
                    }

                    val json = JSONObject(responseBody)
                    val content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    if (content.isBlank()) {
                        throw IllegalStateException("API 返回内容为空")
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                isTestingApi = false,
                apiTestResult = result.fold(
                    onSuccess = { ApiTestResult.Success },
                    onFailure = { e ->
                        val message = when (e) {
                            is java.net.UnknownHostException -> "网络连接失败，请检查网络或 API 地址"
                            is java.net.ConnectException -> "无法连接到服务器，请检查 API 地址"
                            is java.net.SocketTimeoutException -> "连接超时，请检查网络或 API 地址"
                            is IllegalStateException -> e.message ?: "未知错误"
                            else -> "测试失败: ${e.localizedMessage ?: e.toString()}"
                        }
                        ApiTestResult.Failure(message)
                    }
                )
            )
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(apiTestResult = null)
    }
}
