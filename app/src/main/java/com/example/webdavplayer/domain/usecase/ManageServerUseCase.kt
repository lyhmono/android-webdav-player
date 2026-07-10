package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.domain.repository.ServerRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import com.example.webdavplayer.domain.repository.TrustedCertRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 服务器管理用例（§6 T10）：增删改服务器、信任/移除自签证书。
 *
 * 连接流程：
 * 1) [connect] 失败且抛出 [com.example.webdavplayer.domain.exception.CertUntrustedException]
 *    → UI 展示指纹确认弹窗；
 * 2) 用户确认 → [trustCert] 写入永久信任；
 * 3) 再次 [connect] 即可成功。
 *
 * C5（P1）：新增 [selectServer] —— 记住“当前服务器”，驱动播放列表按归属过滤（§7）。
 */
class ManageServerUseCase @Inject constructor(
    private val serverRepository: ServerRepository,
    private val trustedCertRepository: TrustedCertRepository,
    private val settingsRepository: SettingsRepository,
) {
    fun observeServers(): Flow<List<ServerConfig>> = serverRepository.observeAll()
    suspend fun listServers(): List<ServerConfig> = serverRepository.getAll()
    suspend fun getServer(id: String): ServerConfig? = serverRepository.getById(id)

    /** 保存（新建或更新）服务器配置。 */
    suspend fun saveServer(config: ServerConfig) = serverRepository.save(config)

    /** 建立连接（触发信任校验），以 [Result] 返回。 */
    suspend fun connect(config: ServerConfig): Result<Unit> = serverRepository.connect(config)

    /** 删除服务器并清理其信任证书。 */
    suspend fun removeServer(id: String) {
        serverRepository.delete(id)
        trustedCertRepository.removeByServer(id)
    }

    /** C5：切换“当前服务器”并写入偏好（供播放列表按归属过滤）。 */
    suspend fun selectServer(id: String) {
        settingsRepository.setCurrentServerId(id)
    }

    /** C5：观察当前服务器 id（供 UI 标记“当前”）。 */
    fun observeCurrentServerId(): kotlinx.coroutines.flow.Flow<String?> =
        settingsRepository.observeCurrentServerId()

    /** 信任一个自签证书（永久写入）。 */
    suspend fun trustCert(cert: TrustedCert) = trustedCertRepository.add(cert)

    fun observeCerts(): Flow<List<TrustedCert>> = trustedCertRepository.observeAll()
    suspend fun removeCert(id: String) = trustedCertRepository.remove(id)
}
