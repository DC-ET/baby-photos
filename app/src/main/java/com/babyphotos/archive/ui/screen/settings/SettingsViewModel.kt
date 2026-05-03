package com.babyphotos.archive.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.babyphotos.archive.BabyPhotosApp
import com.babyphotos.archive.util.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "gpt-4o-mini",
    val autoAddThreshold: Int = 80,
    val confirmThreshold: Int = 50,
    val maxImageSize: Int = 1024,
    val jpegQuality: Int = 70,
    val concurrencyLimit: Int = 4,
    val scanStartDate: Long = 0L,
    val isSaved: Boolean = false
)

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
            scanStartDate = settingsManager.scanStartDate
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

    fun updateScanStartDate(value: Long) {
        _uiState.value = _uiState.value.copy(scanStartDate = value, isSaved = false)
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

        // Update the recognizer in App
        val app = getApplication<BabyPhotosApp>()
        app.updateRecognizer(
            apiBaseUrl = state.apiBaseUrl.trimEnd('/'),
            apiKey = state.apiKey,
            modelName = state.modelName
        )

        _uiState.value = _uiState.value.copy(isSaved = true)
    }
}
