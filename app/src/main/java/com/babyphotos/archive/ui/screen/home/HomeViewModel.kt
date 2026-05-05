package com.babyphotos.archive.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babyphotos.archive.BabyPhotosApp
import com.babyphotos.archive.data.local.AppDatabase
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.model.ClassificationAction
import com.babyphotos.archive.domain.model.ScanProgress
import com.babyphotos.archive.domain.model.ScanSummary
import com.babyphotos.archive.util.PhotoPermissionUtils
import com.babyphotos.archive.util.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val lastScanSummary: ScanSummary? = null,
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val isConfirming: Boolean = false,
    val isApiConfigured: Boolean = false,
    val hasPhotoPermission: Boolean = false,
    val hasMovePermission: Boolean = false,
    val babyPhotoCount: Int = 0,
    val pendingItems: List<ImageAnalysisEntity> = emptyList(),
    val showMovePermissionDialog: Boolean = false,
    val showScanStartDateDialog: Boolean = false,
    val userMessage: String? = null
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
            isApiConfigured = settingsManager.isApiConfigured(),
            hasPhotoPermission = PhotoPermissionUtils.hasFullReadPermission(application),
            hasMovePermission = PhotoPermissionUtils.hasMovePermission(application)
        )
        loadBabyPhotoCount()
        loadPendingItems()
    }

    override fun onCleared() {
        settingsManager.unregisterListener(prefsListener)
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasPhotoPermission = granted,
            hasMovePermission = PhotoPermissionUtils.hasMovePermission(getApplication())
        )
    }

    fun refreshPhotoPermission() {
        _uiState.value = _uiState.value.copy(
            hasPhotoPermission = PhotoPermissionUtils.hasFullReadPermission(getApplication()),
            hasMovePermission = PhotoPermissionUtils.hasMovePermission(getApplication())
        )
    }

    fun requestStartScan() {
        if (_uiState.value.isScanning) return

        if (settingsManager.scanStartDate <= 0L) {
            _uiState.value = _uiState.value.copy(showScanStartDateDialog = true)
            return
        }

        startScan()
    }

    fun confirmScanStartDate(epochSeconds: Long) {
        settingsManager.scanStartDate = epochSeconds
        _uiState.value = _uiState.value.copy(showScanStartDateDialog = false)
        startScan()
    }

    fun dismissScanStartDateDialog() {
        _uiState.value = _uiState.value.copy(showScanStartDateDialog = false)
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, scanProgress = null)
            try {
                val app = getApplication<BabyPhotosApp>()
                val summary = app.repository.runDailyScan { progress ->
                    _uiState.value = _uiState.value.copy(scanProgress = progress)
                }
                _uiState.value = _uiState.value.copy(
                    lastScanSummary = summary,
                    isScanning = false,
                    scanProgress = null
                )
                loadBabyPhotoCount()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isScanning = false, scanProgress = null)
            }
        }
    }

    fun confirmItem(entity: ImageAnalysisEntity) {
        viewModelScope.launch {
            if (!ensureMovePermission()) return@launch

            val app = getApplication<BabyPhotosApp>()
            _uiState.value = _uiState.value.copy(isConfirming = true, userMessage = null)
            val result = app.repository.confirmAndMove(entity)
            _uiState.value = _uiState.value.copy(
                isConfirming = false,
                userMessage = result.fold(
                    onSuccess = { "已添加到宝宝相册" },
                    onFailure = { "添加失败：${it.message ?: "无法移动照片或视频"}" }
                )
            )
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
            if (!ensureMovePermission()) return@launch

            val app = getApplication<BabyPhotosApp>()
            val items = _uiState.value.pendingItems
            if (items.isEmpty()) return@launch

            _uiState.value = _uiState.value.copy(isConfirming = true, userMessage = null)
            var failures = 0
            items.forEach { entity ->
                if (app.repository.confirmAndMove(entity).isFailure) {
                    failures++
                }
            }
            _uiState.value = _uiState.value.copy(
                isConfirming = false,
                userMessage = if (failures == 0) {
                    "已全部添加到宝宝相册"
                } else {
                    "有 $failures 个文件添加失败，请稍后重试"
                }
            )
        }
    }

    fun rejectAll() {
        viewModelScope.launch {
            _uiState.value.pendingItems.forEach { entity ->
                rejectItem(entity)
            }
        }
    }

    fun dismissMovePermissionDialog() {
        _uiState.value = _uiState.value.copy(showMovePermissionDialog = false)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
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

    private fun ensureMovePermission(): Boolean {
        val hasMovePermission = PhotoPermissionUtils.hasMovePermission(getApplication())
        if (hasMovePermission) {
            _uiState.value = _uiState.value.copy(hasMovePermission = true)
            return true
        }

        _uiState.value = _uiState.value.copy(
            hasMovePermission = false,
            showMovePermissionDialog = true,
            userMessage = "需要授予文件管理权限后才能移动照片或视频"
        )
        return false
    }
}
