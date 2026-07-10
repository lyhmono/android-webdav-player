package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow

/**
 * 服务器配置仓库（§6 T10 / T11）。
 * 凭据通过 EncryptedSharedPreferences（Security-Crypto）加密存储（冷启动优化）。
 *
 * [connect] 在 data 层实现中委托 WebDavClient 完成 TLS 握手与鉴权，
 * 自签证书未信任时抛出 [com.example.webdavplayer.domain.exception.CertUntrustedException]。
 */
interface ServerRepository {
    fun observeAll(): Flow<List<ServerConfig>>
    suspend fun getAll(): List<ServerConfig>
    suspend fun getById(id: String): ServerConfig?
    suspend fun save(config: ServerConfig)
    suspend fun delete(id: String)

    /** 测试/建立连接（触发信任校验；失败以 [Result] 返回）。 */
    suspend fun connect(config: ServerConfig): Result<Unit>
}
