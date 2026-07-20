package com.example.webdavplayer.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.local.dao.CachedMediaDao
import com.example.webdavplayer.data.local.entity.toDomain
import com.example.webdavplayer.data.local.entity.toEntity
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.data.remote.WebDavPath
import com.example.webdavplayer.domain.model.CachedMedia
import com.example.webdavplayer.domain.repository.CacheRepository
import com.example.webdavplayer.domain.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 离线缓存仓库实现（P2）。
 *
 * - 本地文件存储：`cacheDir/cache/$serverId/${path.hashCode()}.bin`
 * - 元数据：Room（[CachedMediaDao]）
 * - 下载：经 [WebDavClient.openStream] 流式写入本地，不整文件加载到内存
 */
@Singleton
class CacheRepositoryImpl @Inject constructor(
    private val webDavClient: WebDavClient,
    private val serverRepository: ServerRepository,
    private val cacheDao: CachedMediaDao,
    @ApplicationContext private val context: Context,
) : CacheRepository {

    override suspend fun download(serverId: String, path: String): Result<CachedMedia> =
        withContext(Dispatchers.IO) {
            Result.runCatching {
                val cfg = serverRepository.getById(serverId)
                    ?: throw IllegalStateException("服务器未找到：$serverId")
                webDavClient.connect(cfg)
                val norm = WebDavPath.normalize(path)
                val name = WebDavPath.nameOf(norm)
                val localFile = cacheFile(serverId, norm)
                localFile.parentFile?.mkdirs()

                val source = webDavClient.openStream(norm)
                localFile.sink().buffer().use { sink ->
                    sink.writeAll(source)
                }
                val cached = CachedMedia(
                    id = "$serverId:$norm",
                    serverId = serverId,
                    path = norm,
                    name = name,
                    size = localFile.length(),
                    downloadedAt = System.currentTimeMillis(),
                )
                cacheDao.upsert(cached.toEntity())
                cached
            }
        }

    override suspend fun getLocalFilePath(serverId: String, path: String): String? =
        withContext(Dispatchers.IO) {
            val norm = WebDavPath.normalize(path)
            val entity = cacheDao.getByServerPath(serverId, norm) ?: return@withContext null
            val file = cacheFile(serverId, norm)
            if (file.exists()) file.absolutePath else null
        }

    override fun observeAll(): Flow<List<CachedMedia>> =
        cacheDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val entity = cacheDao.getById(id) ?: return@withContext
        cacheDao.deleteById(id)
        cacheFile(entity.serverId, entity.path).delete()
    }

    /** 本地缓存文件路径：`cacheDir/cache/$serverId/${path.hashCode()}.bin`。 */
    private fun cacheFile(serverId: String, path: String): File =
        File(context.cacheDir, "cache/$serverId/${path.hashCode()}.bin")
}
