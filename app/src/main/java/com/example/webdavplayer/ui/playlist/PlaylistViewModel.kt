package com.example.webdavplayer.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.repository.PlaylistRepository
import com.example.webdavplayer.domain.usecase.PlayMediaUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 播放列表页 ViewModel（§6 T08）。 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playMedia: PlayMediaUseCase,
) : ViewModel() {

    val items: StateFlow<List<PlaylistItem>> = playlistRepository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mode: StateFlow<PlayMode> = playlistRepository.observeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayMode.SEQUENTIAL)

    fun removeItem(id: String) = viewModelScope.launch {
        playlistRepository.removeItem(id)
    }

    fun clear() = viewModelScope.launch {
        playlistRepository.clear()
    }

    fun setMode(mode: PlayMode) = viewModelScope.launch {
        playlistRepository.setMode(mode)
    }

    fun playItem(item: PlaylistItem) = viewModelScope.launch {
        playMedia(item)
    }

    /** C2：拖拽重排（重编号 addedAt 落库）。 */
    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        playlistRepository.reorder(from, to)
    }
}
