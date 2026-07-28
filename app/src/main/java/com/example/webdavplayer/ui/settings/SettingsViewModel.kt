package com.example.webdavplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.webdavplayer.domain.model.CachedMedia
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.domain.repository.CacheRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import com.example.webdavplayer.domain.usecase.ManageServerUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页 ViewModel：内核选择 + 已信任证书管理 + 离线缓存管理（P2）。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val manageServer: ManageServerUseCase,
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
) : ViewModel() {

    val engineType: StateFlow<EngineType> = settingsRepository.observeEngineType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineType.MEDIA3)

    val certs: StateFlow<List<TrustedCert>> = manageServer.observeCerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 离线缓存列表（按下载时间倒序）。 */
    val cachedMedia: StateFlow<List<CachedMedia>> = cacheRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeCert(id: String) = viewModelScope.launch {
        manageServer.removeCert(id)
    }

    /** 删除指定缓存（清理本地文件 + Room 记录）。 */
    fun deleteCache(id: String) = viewModelScope.launch {
        cacheRepository.delete(id)
    }
}
