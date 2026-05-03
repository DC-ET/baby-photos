package com.babyphotos.archive.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.babyphotos.archive.BabyPhotosApp
import com.babyphotos.archive.data.local.AppDatabase
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.model.ClassificationAction
import com.babyphotos.archive.domain.model.ScanSummary
import com.babyphotos.archive.util.SettingsManager
import com.babyphotos.archive.worker.DailyScanWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val lastScanSummary: ScanSummary? = null,
    val isScanning: Boolean = false,
    val isApiConfigured: Boolean = false,
    val hasPhotoPermission: Boolean = false,
    val babyPhotoCount: Int = 0,
    val pendingItems: List<ImageAnalysisEntity> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val dao = AppDatabase.getInstance(application).imageAnalysisDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "api_base_url" || key == "api_key") {
            _uiState.value = _uiState.value.copy(
                isApiConfigured = settingsManager.isApiConfigured()
            )
        }
    }

    init {
        settingsManager.registerListener(prefsListener)
        _uiState.value = _uiState.value.copy(
            isApiConfigured = settingsManager.isApiConfigured()
        )
        loadBabyPhotoCount()
        loadPendingItems()
    }

    override fun onCleared() {
        settingsManager.unregisterListener(prefsListener)
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPhotoPermission = granted)
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            try {
                val app = getApplication<BabyPhotosApp>()
                val summary = app.repository.runDailyScan()
                _uiState.value = _uiState.value.copy(
                    lastScanSummary = summary,
                    isScanning = false
                )
                loadBabyPhotoCount()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun confirmItem(entity: ImageAnalysisEntity) {
        viewModelScope.launch {
            val updated = entity.copy(action = ClassificationAction.AUTO_ADD.name)
            dao.insert(updated)
        }
    }

    fun rejectItem(entity: ImageAnalysisEntity) {
        viewModelScope.launch {
            val updated = entity.copy(action = ClassificationAction.IGNORE.name)
            dao.insert(updated)
        }
    }

    fun confirmAll() {
        viewModelScope.launch {
            _uiState.value.pendingItems.forEach { entity ->
                confirmItem(entity)
            }
        }
    }

    fun rejectAll() {
        viewModelScope.launch {
            _uiState.value.pendingItems.forEach { entity ->
                rejectItem(entity)
            }
        }
    }

    fun startManualWorkManagerScan() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val scanRequest = OneTimeWorkRequestBuilder<DailyScanWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication()).enqueue(scanRequest)
    }

    private fun loadBabyPhotoCount() {
        viewModelScope.launch {
            dao.getBabyPhotos().collect { photos ->
                _uiState.value = _uiState.value.copy(babyPhotoCount = photos.size)
            }
        }
    }

    private fun loadPendingItems() {
        viewModelScope.launch {
            dao.getPendingConfirmations().collect { items ->
                _uiState.value = _uiState.value.copy(pendingItems = items)
            }
        }
    }
}
