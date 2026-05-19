package com.babyphotos.archive.ui.screen.album

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babyphotos.archive.BabyPhotosApp
import com.babyphotos.archive.domain.model.BabyAlbumDateGrouper
import com.babyphotos.archive.domain.model.BabyAlbumDateSection
import com.babyphotos.archive.domain.model.BabyAlbumMedia
import com.babyphotos.archive.util.PhotoPermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BabyPhotosApp

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    private var mediaObserver: ContentObserver? = null

    fun onScreenVisible() {
        registerMediaObserver()
        refresh()
    }

    fun onScreenHidden() {
        unregisterMediaObserver()
    }

    fun refresh() {
        if (!PhotoPermissionUtils.hasFullReadPermission(getApplication())) {
            _uiState.update {
                it.copy(
                    displayState = AlbumDisplayState.PermissionDenied,
                    sections = emptyList(),
                    flatItems = emptyList()
                )
            }
            return
        }

        _uiState.update { it.copy(displayState = AlbumDisplayState.Loading) }
        viewModelScope.launch {
            val items = app.babyAlbumReader.fetchAllMedia()
            val sections = BabyAlbumDateGrouper.group(items)
            val flat = sections.flatMap { section -> section.items }
            _uiState.update {
                it.copy(
                    displayState = if (flat.isEmpty()) AlbumDisplayState.Empty else AlbumDisplayState.Content,
                    sections = sections,
                    flatItems = flat
                )
            }
        }
    }

    fun openViewer(index: Int) {
        _uiState.update { it.copy(selectedIndex = index) }
    }

    fun closeViewer() {
        _uiState.update { it.copy(selectedIndex = null) }
    }

    private fun registerMediaObserver() {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }
        mediaObserver = observer
        val resolver = getApplication<Application>().contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    private fun unregisterMediaObserver() {
        mediaObserver?.let { observer ->
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        }
        mediaObserver = null
    }
}

enum class AlbumDisplayState {
    Loading,
    PermissionDenied,
    Empty,
    Content
}

data class AlbumUiState(
    val displayState: AlbumDisplayState = AlbumDisplayState.Loading,
    val sections: List<BabyAlbumDateSection> = emptyList(),
    val flatItems: List<BabyAlbumMedia> = emptyList(),
    val selectedIndex: Int? = null
)
