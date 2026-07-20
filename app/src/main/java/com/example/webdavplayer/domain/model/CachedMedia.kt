package com.example.webdavplayer.domain.model

/** 离线缓存文件（领域模型，纯 Kotlin）。 */
data class CachedMedia(
    val id: String,            // "$serverId:$path"
    val serverId: String,
    val path: String,
    val name: String,
    val size: Long,
    val downloadedAt: Long,
)
