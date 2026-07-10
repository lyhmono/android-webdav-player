package com.example.webdavplayer.data.repository

import com.example.webdavplayer.data.local.dao.TrustedCertDao
import com.example.webdavplayer.data.local.entity.toDomain
import com.example.webdavplayer.data.local.entity.toEntity
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.domain.repository.TrustedCertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 已信任自签证书仓库实现（§1.4）。 */
@Singleton
class TrustedCertRepositoryImpl @Inject constructor(
    private val dao: TrustedCertDao,
) : TrustedCertRepository {

    override fun observeAll(): Flow<List<TrustedCert>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getByServer(serverId: String): List<TrustedCert> =
        dao.getByServer(serverId).map { it.toDomain() }

    override suspend fun getFingerprints(serverId: String): List<String> =
        dao.getFingerprints(serverId)

    override suspend fun add(cert: TrustedCert) = dao.insert(cert.toEntity())

    override suspend fun remove(id: String) = dao.deleteById(id)

    override suspend fun removeByServer(serverId: String) = dao.deleteByServer(serverId)
}
