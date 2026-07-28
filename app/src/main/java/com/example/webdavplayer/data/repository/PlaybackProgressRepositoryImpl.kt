package com.example.webdavplayer.data.repository

import com.example.webdavplayer.data.local.dao.PlaybackProgressDao
import com.example.webdavplayer.data.local.entity.PlaybackProgressEntity
import com.example.webdavplayer.domain.model.PlaybackProgress
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放进度仓库实现（C3 / §5.2）：基于 Room 的 [PlaybackProgressDao]。
 */
@Singleton
class PlaybackProgressRepositoryImpl @Inject constructor(
    private val dao: PlaybackProgressDao,
) : PlaybackProgressRepository {

    override suspend fun save(progress: PlaybackProgress) = dao.upsert(progress.toEntity())

    override suspend fun get(serverId: String, path: String): PlaybackProgress? =
        dao.get(serverId, path)?.toDomain()

    override suspend fun clear(serverId: String, path: String) = dao.delete(serverId, path)

    override suspend fun clearServer(serverId: String) = dao.deleteByServerId(serverId)

    override suspend fun clearAll() = dao.clearAll()
}

/** 领域模型 → 实体。 */
private fun PlaybackProgress.toEntity(): PlaybackProgressEntity = PlaybackProgressEntity(
    serverId = serverId,
    path = path,
    positionMs = positionMs,
    updatedAt = updatedAt,
)

/** 实体 → 领域模型。 */
private fun PlaybackProgressEntity.toDomain(): PlaybackProgress = PlaybackProgress(
    serverId = serverId,
    path = path,
    positionMs = positionMs,
    updatedAt = updatedAt,
)
