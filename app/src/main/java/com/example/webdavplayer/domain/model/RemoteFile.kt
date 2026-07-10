package com.example.webdavplayer.domain.model

/**
 * 远程文件/目录条目（PROPFIND 结果映射，§4.1）。
 * 作为 Room 缓存真相源的行模型（与 RemoteFileEntity 互转）。
 */
data class RemoteFile(
    val id: String,
    val serverId: String,
    val parentPath: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val contentType: String,
    val lastModified: Long,
    val eTag: String,
    val mediaType: MediaType,
)
