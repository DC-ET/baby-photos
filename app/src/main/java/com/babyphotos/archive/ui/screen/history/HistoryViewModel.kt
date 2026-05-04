package com.babyphotos.archive.ui.screen.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babyphotos.archive.BabyPhotosApp
import com.babyphotos.archive.data.local.AppDatabase
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.model.ClassificationAction
import com.babyphotos.archive.data.repository.RemoveFromBabyAlbumOutcome
import com.babyphotos.archive.util.PhotoPermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class HistoryFilter { ALL, BABY, CONFIRMED, IGNORED }

data class HistoryUiState(
    val allItems: List<ImageAnalysisEntity> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val movingItemIds: Set<String> = emptySet(),
    val showMovePermissionDialog: Boolean = false,
    val isCleaningStale: Boolean = false,
    val userMessage: String? = null
) {
    val filteredItems: List<ImageAnalysisEntity>
        get() = when (filter) {
            HistoryFilter.ALL -> allItems
            HistoryFilter.BABY -> allItems.filter { it.containsBaby }
            HistoryFilter.CONFIRMED -> allItems.filter {
                it.action == ClassificationAction.AUTO_ADD.name && it.containsBaby
            }
            HistoryFilter.IGNORED -> allItems.filter { it.action == ClassificationAction.IGNORE.name }
        }
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).imageAnalysisDao()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            dao.getAll().collect { items ->
                _uiState.value = _uiState.value.copy(allItems = items)
            }
        }
    }

    fun setFilter(filter: HistoryFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun moveIgnoredToBabyAlbum(entity: ImageAnalysisEntity) {
        if (entity.action != ClassificationAction.IGNORE.name) return
        if (_uiState.value.movingItemIds.contains(entity.id)) return

        viewModelScope.launch {
            if (!ensureMovePermission()) return@launch

            val app = getApplication<BabyPhotosApp>()
            _uiState.value = _uiState.value.copy(
                movingItemIds = _uiState.value.movingItemIds + entity.id,
                userMessage = null
            )

            val result = app.repository.confirmAndMove(entity)
            _uiState.value = _uiState.value.copy(
                movingItemIds = _uiState.value.movingItemIds - entity.id,
                userMessage = result.fold(
                    onSuccess = { "已移动到宝宝相册" },
                    onFailure = { "移动失败：${it.message ?: "无法移动照片或视频"}" }
                )
            )
        }
    }

    fun removeFromBabyAlbum(entity: ImageAnalysisEntity) {
        val archivedPath = entity.movedTo ?: return
        if (_uiState.value.movingItemIds.contains(entity.id)) return

        viewModelScope.launch {
            val fileExists = withContext(Dispatchers.IO) { File(archivedPath).exists() }
            if (fileExists && !ensureMovePermission()) return@launch

            val app = getApplication<BabyPhotosApp>()
            _uiState.value = _uiState.value.copy(
                movingItemIds = _uiState.value.movingItemIds + entity.id,
                userMessage = null
            )

            val result = app.repository.removeFromBabyAlbum(entity)
            _uiState.value = _uiState.value.copy(
                movingItemIds = _uiState.value.movingItemIds - entity.id,
                userMessage = result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is RemoveFromBabyAlbumOutcome.MovedBack -> "已移回原始位置"
                            RemoveFromBabyAlbumOutcome.ClearedStaleRecord ->
                                "文件已不存在，已清除相关记录"
                        }
                    },
                    onFailure = { "移除失败：${it.message ?: "无法移回照片或视频"}" }
                )
            )
        }
    }

    fun cleanStaleRecords() {
        if (_uiState.value.isCleaningStale) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCleaningStale = true, userMessage = null)

            val currentItems = _uiState.value.allItems
            val staleIds = withContext(Dispatchers.IO) {
                currentItems.filter { entity ->
                    val currentPath = entity.movedTo ?: entity.path
                    !File(currentPath).exists()
                }.map { it.id }
            }

            if (staleIds.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isCleaningStale = false,
                    userMessage = "没有发现已失效的记录"
                )
            } else {
                dao.deleteByIds(staleIds)
                _uiState.value = _uiState.value.copy(
                    isCleaningStale = false,
                    userMessage = "已清理 ${staleIds.size} 条无效记录"
                )
            }
        }
    }

    fun refreshMovePermission() {
        if (PhotoPermissionUtils.hasMovePermission(getApplication())) {
            _uiState.value = _uiState.value.copy(showMovePermissionDialog = false)
        }
    }

    fun dismissMovePermissionDialog() {
        _uiState.value = _uiState.value.copy(showMovePermissionDialog = false)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    private fun ensureMovePermission(): Boolean {
        if (PhotoPermissionUtils.hasMovePermission(getApplication())) {
            return true
        }

        _uiState.value = _uiState.value.copy(
            showMovePermissionDialog = true,
            userMessage = "需要授予文件管理权限后才能移动照片或视频"
        )
        return false
    }
}
