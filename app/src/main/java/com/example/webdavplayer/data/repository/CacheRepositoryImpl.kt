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
import com.example.webdavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 离线缓存仓库实现（P2）。
 *
 * - 本地文件存储：`downloadRoot/$serverId/${sha8}_${originalName}`
 * - 元数据 + 真实落盘路径：Room（[CachedMediaDao]）
 * - 下载：经 [WebDavClient.openStream] 流式写入本地，不整文件加载到内存
 * - 删除：v5 起优先按 entity.localPath 精确清理；旧记录（localPath=null）fallback 到拼接路径兜底
 */
@Singleton
class CacheRepositoryImpl @Inject constructor(
    private val webDavClient: WebDavClient,
    private val serverRepository: ServerRepository,
    private val cacheDao: CachedMediaDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : CacheRepository {

    /** 下载根目录：优先用户设置的 downloadDir，否则默认 cacheDir/cache。 */
    private suspend fun downloadRoot(): File {
        val custom = settingsRepository.getDownloadDir()
        return if (!custom.isNullOrBlank()) {
            File(custom)
        } else {
            File(context.cacheDir, "cache")
        }
    }

    override suspend fun download(serverId: String, path: String): Result<CachedMedia> =
        withContext(Dispatchers.IO) {
            Result.runCatching {
                val cfg = serverRepository.getById(serverId)
                    ?: throw IllegalStateException("服务器未找到：$serverId")
                webDavClient.connect(cfg)
                val norm = WebDavPath.normalize(path)
                val name = WebDavPath.nameOf(norm)
                val root = downloadRoot()
                // 按 serverId 分子目录 + 保留原始文件名（前缀 sha8 防碰撞）
                val safeName = "${sha256Hex(norm).take(8)}_$name"
                val localFile = File(File(root, serverId), safeName)
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
                // v5：把真实落盘绝对路径一并入库，删除时按此精确清理
                cacheDao.upsert(cached.toEntity(localPath = localFile.absolutePath))
                cached
            }
        }

    override suspend fun getLocalFilePath(serverId: String, path: String): String? =
        withContext(Dispatchers.IO) {
            val norm = WebDavPath.normalize(path)
            val entity = cacheDao.getByServerPath(serverId, norm) ?: return@withContext null
            // v5：优先用 entity 记录的真实路径
            val file = entity.localPath?.let { File(it) }
                ?: localFileOf(serverId, norm, entity.name)
            if (file.exists()) file.absolutePath else null
        }

    override fun observeAll(): Flow<List<CachedMedia>> =
        cacheDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val entity = cacheDao.getById(id) ?: return@withContext
        cacheDao.deleteById(id)
        // v5：优先按 entity.localPath 精确删；旧记录 fallback 到拼接路径
        val file = entity.localPath?.let { File(it) }
            ?: localFileOf(entity.serverId, entity.path, entity.name)
        file.delete()
    }

    override suspend fun getEffectiveRootDir(): String =
        withContext(Dispatchers.IO) { downloadRoot().absolutePath }

    /** 旧记录兜底：按 `downloadRoot/$serverId/${sha8}_${name}` 拼接定位文件。 */
    private suspend fun localFileOf(serverId: String, path: String, name: String): File {
        val root = downloadRoot()
        val safeName = "${sha256Hex(path).take(8)}_$name"
        return File(File(root, serverId), safeName)
    }
}

/**
 * 缓存文件名键：对规范化路径取 SHA-256 十六进制串。
 * 相比 [String.hashCode] 可避免不同路径哈希碰撞导致的缓存互相覆盖/误命中（§正确性）。
 */
internal fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
