package com.example.webdavplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.webdavplayer.domain.model.CachedMedia

/**
 * 离线缓存元数据实体（Room 真相源）。
 *
 * 本地文件保存在 `context.cacheDir/cache/$serverId/${path.hashCode()}.bin`，
 * 实体仅记录元数据；删除时一并清理本地文件。
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
)

fun CachedMediaEntity.toDomain(): CachedMedia = CachedMedia(
    id = id,
    serverId = serverId,
    path = path,
    name = name,
    size = size,
    downloadedAt = downloadedAt,
)

fun CachedMedia.toEntity(): CachedMediaEntity = CachedMediaEntity(
    id = id,
    serverId = serverId,
    path = path,
    name = name,
    size = size,
    downloadedAt = downloadedAt,
)
