package com.example.webdavplayer.ui.servers

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.exception.CertUntrustedException
import com.example.webdavplayer.domain.model.AuthType
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.domain.usecase.ManageServerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** 自签证书确认弹窗状态。 */
data class CertDialogState(
    val fingerprint: String,
    val issuer: String,
    val pendingConfig: ServerConfig,
)

/** 服务器配置页（新增/编辑）ViewModel（§1.4 / §6 T10）。 */
@HiltViewModel
class ServerConfigViewModel @Inject constructor(
    private val manageServer: ManageServerUseCase,
) : ViewModel() {

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _certDialog = MutableStateFlow<CertDialogState?>(null)
    val certDialog: StateFlow<CertDialogState?> = _certDialog.asStateFlow()

    private val _savedServerId = MutableStateFlow<String?>(null)
    val savedServerId: StateFlow<String?> = _savedServerId.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var pendingConfig: ServerConfig? = null

    /** 尝试连接并保存服务器。 */
    fun attemptConnect(config: ServerConfig) {
        _connecting.value = true
        _lastError.value = null
        viewModelScope.launch {
            when (val r = manageServer.connect(config)) {
                is Result.Success -> {
                    manageServer.saveServer(config)
                    _connecting.value = false
                    _savedServerId.value = config.id
                }
                is Result.Error -> {
                    _connecting.value = false
                    val t = r.throwable
                    if (t is CertUntrustedException) {
                        pendingConfig = config
                        _certDialog.value = CertDialogState(
                            fingerprint = t.sha256Fingerprint,
                            issuer = t.issuer,
                            pendingConfig = config,
                        )
                    } else {
                        _lastError.value = t.message ?: t.toString()
                    }
                }
            }
        }
    }

    /** 用户确认信任自签证书后，再次连接。 */
    fun trustAndContinue() {
        val dialog = _certDialog.value ?: return
        viewModelScope.launch {
            manageServer.trustCert(
                TrustedCert(
                    id = UUID.randomUUID().toString(),
                    serverId = dialog.pendingConfig.id,
                    sha256Fingerprint = dialog.fingerprint,
                    issuer = dialog.issuer,
                    trustedAt = System.currentTimeMillis(),
                ),
            )
            _certDialog.value = null
            attemptConnect(dialog.pendingConfig)
        }
    }

    fun dismissCertDialog() {
        _certDialog.value = null
    }

    /** 取出并清除「已保存」标记（供界面跳转）。 */
    fun consumeSaved(): String? {
        val id = _savedServerId.value
        _savedServerId.value = null
        return id
    }

    fun consumeError() {
        _lastError.value = null
    }

    /** 编辑模式：加载已有服务器配置填充表单。 */
    suspend fun loadServerForEdit(id: String): ServerConfig? {
        return manageServer.getServer(id)
    }
}
