package com.example.webdavplayer.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.webdavplayer.data.local.dao.RemoteFileDao
import com.example.webdavplayer.data.local.entity.toDomain
import com.example.webdavplayer.data.local.entity.toEntity
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.data.remote.WebDavPath
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.repository.BrowseRepository
import com.example.webdavplayer.domain.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.Source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 浏览仓库实现（§6 T06）。
 * 真相源 = Room；大目录用 Paging3（§1.3）。
 */
@Singleton
class BrowseRepositoryImpl @Inject constructor(
    private val webDavClient: WebDavClient,
    private val remoteFileDao: RemoteFileDao,
    private val serverRepository: ServerRepository,
) : BrowseRepository {

    override fun getDirectory(serverId: String, path: String): Flow<PagingData<RemoteFile>> {
        val norm = WebDavPath.normalize(path)
        return Pager(
            config = PagingConfig(pageSize = 60, enablePlaceholders = false),
            pagingSourceFactory = { remoteFileDao.pagingSource(serverId, norm) },
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override suspend fun refreshDirectory(serverId: String, path: String) = withContext(Dispatchers.IO) {
        val cfg = serverRepository.getById(serverId) ?: return@withContext
        webDavClient.connect(cfg)
        val norm = WebDavPath.normalize(path)
        val files = webDavClient.listDirectory(norm, 1)
        remoteFileDao.clearDirectory(serverId, norm)
        remoteFileDao.upsertAll(files.map { it.toEntity() })
    }

    override suspend fun listDirectory(serverId: String, path: String): List<RemoteFile> =
        withContext(Dispatchers.IO) {
            val cfg = serverRepository.getById(serverId) ?: return@withContext emptyList()
            webDavClient.connect(cfg)
            webDavClient.listDirectory(WebDavPath.normalize(path), 1)
        }

    override suspend fun rename(serverId: String, fromPath: String, toName: String) =
        withContext(Dispatchers.IO) {
            val cfg = serverRepository.getById(serverId)
                ?: throw IllegalStateException("server not found: $serverId")
            webDavClient.connect(cfg)
            webDavClient.rename(WebDavPath.normalize(fromPath), toName)
        }

    override suspend fun move(serverId: String, fromPath: String, toPath: String) =
        withContext(Dispatchers.IO) {
            val cfg = serverRepository.getById(serverId)
                ?: throw IllegalStateException("server not found: $serverId")
            webDavClient.connect(cfg)
            webDavClient.move(WebDavPath.normalize(fromPath), WebDavPath.normalize(toPath))
        }

    override suspend fun delete(serverId: String, path: String) = withContext(Dispatchers.IO) {
        val cfg = serverRepository.getById(serverId)
            ?: throw IllegalStateException("server not found: $serverId")
        webDavClient.connect(cfg)
        webDavClient.delete(WebDavPath.normalize(path))
    }

    override suspend fun upload(
        serverId: String,
        path: String,
        source: Source,
        size: Long?,
    ) = withContext(Dispatchers.IO) {
        val cfg = serverRepository.getById(serverId)
            ?: throw IllegalStateException("server not found: $serverId")
        webDavClient.connect(cfg)
        webDavClient.upload(WebDavPath.normalize(path), source, size)
    }
}
