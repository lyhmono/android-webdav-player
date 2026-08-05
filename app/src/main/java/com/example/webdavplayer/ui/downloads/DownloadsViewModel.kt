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

    /** 用户配置的下载目录原始值；null = 未自定义。 */
    val downloadDir = settingsRepository.observeDownloadDir()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 真实生效的根目录绝对路径（自定义 or 默认 cacheDir/cache）。 */
    private val _effectiveRootDir = MutableStateFlow<String?>(null)
    val effectiveRootDir: StateFlow<String?> = _effectiveRootDir.asStateFlow()

    init {
        viewModelScope.launch {
            _effectiveRootDir.value = cacheRepository.getEffectiveRootDir()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { cacheRepository.delete(id) }
    }

    fun setDownloadDir(path: String?) {
        viewModelScope.launch {
            settingsRepository.setDownloadDir(path)
            // 路径变更后刷新生效目录展示
            _effectiveRootDir.value = cacheRepository.getEffectiveRootDir()
        }
    }
}
