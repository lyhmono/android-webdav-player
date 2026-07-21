package com.example.webdavplayer.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.webdavplayer.data.local.dao.DirectoryMetaDao
import com.example.webdavplayer.data.local.dao.RemoteFileDao
import com.example.webdavplayer.data.local.entity.DirectoryMetaEntity
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
    private val directoryMetaDao: DirectoryMetaDao,
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
        // 记录本次成功刷新的时间戳，供 TTL 条件刷新判断（§1.3 优化）。
        directoryMetaDao.upsert(
            DirectoryMetaEntity(
                id = metaId(serverId, norm),
                serverId = serverId,
                parentPath = norm,
                lastRefreshedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun refreshIfStale(
        serverId: String,
        path: String,
        maxAgeMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (isCacheFresh(serverId, path, maxAgeMs)) return@withContext
        refreshDirectory(serverId, path)
    }

    override suspend fun isCacheFresh(
        serverId: String,
        path: String,
        maxAgeMs: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val norm = WebDavPath.normalize(path)
        val meta = directoryMetaDao.get(serverId, norm) ?: return@withContext false
        if (System.currentTimeMillis() - meta.lastRefreshedAt > maxAgeMs) return@withContext false
        // 已刷新且未超龄，还需缓存非空才视为可用。
        remoteFileDao.countDirectory(serverId, norm) > 0
    }

    override suspend fun getLastRefreshedAt(serverId: String, path: String): Long? =
        withContext(Dispatchers.IO) {
            directoryMetaDao.get(serverId, WebDavPath.normalize(path))?.lastRefreshedAt
        }

    override suspend fun invalidateDirectory(serverId: String, path: String) =
        withContext(Dispatchers.IO) {
            directoryMetaDao.clearDirectory(serverId, WebDavPath.normalize(path))
        }

    /** 目录元数据复合主键：`"$serverId::$parentPath"`。 */
    private fun metaId(serverId: String, parentPath: String): String = "$serverId::$parentPath"

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
            // 一致性：改名发生在同一父目录，使其缓存失效以便下次刷新反映新名称。
            invalidateDirectory(serverId, WebDavPath.parentOf(fromPath))
        }

    override suspend fun move(serverId: String, fromPath: String, toPath: String) =
        withContext(Dispatchers.IO) {
            val cfg = serverRepository.getById(serverId)
                ?: throw IllegalStateException("server not found: $serverId")
            webDavClient.connect(cfg)
            webDavClient.move(WebDavPath.normalize(fromPath), WebDavPath.normalize(toPath))
            // 一致性：源目录（文件移出）与目标目录（文件移入）均需失效。
            invalidateDirectory(serverId, WebDavPath.parentOf(fromPath))
            invalidateDirectory(serverId, WebDavPath.parentOf(toPath))
        }

    override suspend fun delete(serverId: String, path: String) = withContext(Dispatchers.IO) {
        val cfg = serverRepository.getById(serverId)
            ?: throw IllegalStateException("server not found: $serverId")
        webDavClient.connect(cfg)
        webDavClient.delete(WebDavPath.normalize(path))
        // 一致性：被删条目的父目录需失效。
        invalidateDirectory(serverId, WebDavPath.parentOf(path))
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
        // 一致性：上传落地的父目录需失效。
        invalidateDirectory(serverId, WebDavPath.parentOf(path))
    }
}
