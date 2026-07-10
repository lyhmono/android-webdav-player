package com.example.webdavplayer.domain.model

/**
 * 播放列表项（§4.1）。
 * 路径为相对服务器的规范化路径（见 WebDavPath）。
 */
data class PlaylistItem(
    val id: String,
    val serverId: String,
    val path: String,
    val name: String,
    val mediaType: MediaType,
    val durationMs: Long,
    val addedAt: Long,
)
