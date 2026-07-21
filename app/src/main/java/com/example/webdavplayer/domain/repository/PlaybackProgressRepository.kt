package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.PlaybackProgress

/**
 * 播放进度仓库接口（C3 / §5.2）。
 * 与播放列表表分离，按 (serverId, path) 读写断点。
 */
interface PlaybackProgressRepository {
    /** 保存（upsert）某一媒体的断点位置。 */
    suspend fun save(progress: PlaybackProgress)

    /** 读取某一媒体的断点（无则 null）。 */
    suspend fun get(serverId: String, path: String): PlaybackProgress?

    /** 清除某一媒体的断点（正常播放结束后调用）。 */
    suspend fun clear(serverId: String, path: String)

    /** 清除某一服务器的全部断点（删除服务器时清理孤儿行）。 */
    suspend fun clearServer(serverId: String)

    /** 清空全部断点。 */
    suspend fun clearAll()
}
