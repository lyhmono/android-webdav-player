package com.example.webdavplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.webdavplayer.domain.model.CachedMedia

/**
 * 离线缓存元数据实体（Room 真相源）。
 *
 * 本地文件保存到 `downloadRoot/$serverId/${sha8}_${originalName}`；
 * entity 在 v5 起额外记录 [localPath]，删除时按此路径精确清理——
 * 避免"用户改 downloadDir 后旧文件成孤儿"风险。
 * 旧记录 [localPath] 为 null，删除时 fallback 到 `downloadRoot + sha8拼名` 兜底。
 */
@Entity(tableName = "cached_media")
data class CachedMediaEntity(
    /** `"$serverId:$path"` — 与 [com.example.webdavplayer.domain.model.PlaylistItem] id 同源。 */
    @PrimaryKey val id: String,
    val serverId: String,
    val path: String,
    val name: String,
    val size: Long,
    val downloadedAt: Long,
    /** v5：本机文件绝对路径（下载时落库）。null 表示旧记录或迁移前的数据。 */
    val localPath: String? = null,
)

fun CachedMediaEntity.toDomain(): CachedMedia = CachedMedia(
    id = id,
    serverId = serverId,
    path = path,
    name = name,
    size = size,
    downloadedAt = downloadedAt,
)

fun CachedMedia.toEntity(localPath: String? = null): CachedMediaEntity = CachedMediaEntity(
    id = id,
    serverId = serverId,
    path = path,
    name = name,
    size = size,
    downloadedAt = downloadedAt,
    localPath = localPath,
)
