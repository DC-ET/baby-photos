package com.babyphotos.archive.ui.screen.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babyphotos.archive.data.local.AppDatabase
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HistoryFilter { ALL, BABY, CONFIRMED, IGNORED }

data class HistoryUiState(
    val allItems: List<ImageAnalysisEntity> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL
) {
    val filteredItems: List<ImageAnalysisEntity>
        get() = when (filter) {
            HistoryFilter.ALL -> allItems
            HistoryFilter.BABY -> allItems.filter { it.containsBaby }
            HistoryFilter.CONFIRMED -> allItems.filter {
                it.action == "AUTO_ADD" && it.containsBaby
            }
            HistoryFilter.IGNORED -> allItems.filter { it.action == "IGNORE" }
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
}
