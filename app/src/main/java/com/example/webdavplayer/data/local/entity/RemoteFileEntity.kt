package com.example.webdavplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.RemoteFile

/**
 * 远程文件缓存实体（Room 真相源，§1.3）。
 * 与 [com.example.webdavplayer.domain.model.RemoteFile] 互转。
 */
@Entity(tableName = "remote_files")
data class RemoteFileEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val parentPath: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val contentType: String,
    val lastModified: Long,
    val eTag: String,
    val mediaType: MediaType,
    /** 缓存写入时间戳（用于 TTL 失效策略）。 */
    val cachedAt: Long = System.currentTimeMillis(),
)

fun RemoteFileEntity.toDomain(): RemoteFile = RemoteFile(
    id = id,
    serverId = serverId,
    parentPath = parentPath,
    name = name,
    isDirectory = isDirectory,
    size = size,
    contentType = contentType,
    lastModified = lastModified,
    eTag = eTag,
    mediaType = mediaType,
)

fun RemoteFile.toEntity(): RemoteFileEntity = RemoteFileEntity(
    id = id,
    serverId = serverId,
    parentPath = parentPath,
    name = name,
    isDirectory = isDirectory,
    size = size,
    contentType = contentType,
    lastModified = lastModified,
    eTag = eTag,
    mediaType = mediaType,
    cachedAt = System.currentTimeMillis(),
)
