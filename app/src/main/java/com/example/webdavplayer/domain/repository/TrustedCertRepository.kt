package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.TrustedCert
import kotlinx.coroutines.flow.Flow

/** 已信任自签证书仓库（§1.4）。可读写 Room 中的 TrustedCert。 */
interface TrustedCertRepository {
    fun observeAll(): Flow<List<TrustedCert>>
    suspend fun getByServer(serverId: String): List<TrustedCert>
    suspend fun getFingerprints(serverId: String): List<String>
    suspend fun add(cert: TrustedCert)
    suspend fun remove(id: String)
    suspend fun removeByServer(serverId: String)
}
