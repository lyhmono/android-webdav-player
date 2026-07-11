package com.example.webdavplayer.ui.servers

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.usecase.ManageServerUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 服务器列表页 ViewModel（§6 T10 + C5）。 */
@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val manageServer: ManageServerUseCase,
) : ViewModel() {

    val servers: StateFlow<List<ServerConfig>> = manageServer.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** C5：当前服务器 id，用于列表标记“当前”。 */
    val currentServerId: StateFlow<String?> = manageServer.observeCurrentServerId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun removeServer(id: String) = viewModelScope.launch {
        manageServer.removeServer(id)
    }

    /** C5：记住当前服务器（供播放列表按归属过滤），随后由 UI 导航到 browse/{id}。 */
    fun selectServer(id: String) = viewModelScope.launch {
        manageServer.selectServer(id)
    }
}
