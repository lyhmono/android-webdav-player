package com.example.webdavplayer.domain.model

/**
 * 播放进度断点（C3 / §5.1）。
 *
 * 与播放列表解耦：以 (serverId, path) 唯一标识一条媒体的断点位置，
 * 用于“退出后再次进入从断点续播”。kernel-agnostic：仅记录位置，不关心内核。
 *
 * @param serverId 归属服务器（与 [PlaylistItem.serverId] 对应）
 * @param path 相对服务器的规范化路径（与 [PlaylistItem.path] 对应）
 * @param positionMs 断点位置（毫秒）
 * @param updatedAt 最后更新时间戳（毫秒，System.currentTimeMillis）
 */
data class PlaybackProgress(
    val serverId: String,
    val path: String,
    val positionMs: Long,
    val updatedAt: Long = 0L,
)
