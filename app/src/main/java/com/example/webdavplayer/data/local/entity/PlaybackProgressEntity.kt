package com.example.webdavplayer.data.local.entity

import androidx.room.Entity

/**
 * 播放进度实体（C3 / §5.1）。
 * 与播放列表表分离：以 (serverId, path) 复合主键唯一标识一条媒体的断点位置。
 */
@Entity(tableName = "playback_progress", primaryKeys = ["serverId", "path"])
data class PlaybackProgressEntity(
    val serverId: String,
    val path: String,
    val positionMs: Long,
    val updatedAt: Long,
)
