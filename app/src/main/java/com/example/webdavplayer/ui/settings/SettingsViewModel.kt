package com.example.webdavplayer.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.domain.usecase.ManageServerUseCase
import com.example.webdavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页 ViewModel（§6 T09/T10）：内核选择 + 已信任证书管理。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val manageServer: ManageServerUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val engineType: StateFlow<EngineType> = settingsRepository.observeEngineType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineType.MEDIA3)

    val certs: StateFlow<List<TrustedCert>> = manageServer.observeCerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeCert(id: String) = viewModelScope.launch {
        manageServer.removeCert(id)
    }
}
