package com.example.webdavplayer.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webdavplayer.domain.repository.CacheRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val cacheRepository: CacheRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 已下载文件列表（按下载时间倒序）。 */
    val downloads = cacheRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前下载目录路径；null = 默认（cacheDir）。 */
    val downloadDir = settingsRepository.observeDownloadDir()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete(id: String) {
        viewModelScope.launch { cacheRepository.delete(id) }
    }

    fun setDownloadDir(path: String?) {
        viewModelScope.launch { settingsRepository.setDownloadDir(path) }
    }
}
