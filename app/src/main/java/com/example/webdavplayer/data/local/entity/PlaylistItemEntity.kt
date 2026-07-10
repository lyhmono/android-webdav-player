package com.example.webdavplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlaylistItem

/**
 * 播放列表项实体（§4.1）。
 *
 * P0 播放列表为内存态（[com.example.webdavplayer.data.repository.PlaylistRepositoryImpl]），
 * 本实体与 DAO 作为 P1「持久化到 Room」（T12）的预留缝，当前未串联进 P0 数据流。
 */
@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val path: String,
    val name: String,
    val mediaType: MediaType,
    val durationMs: Long,
    val addedAt: Long,
)

fun PlaylistItemEntity.toDomain(): PlaylistItem = PlaylistItem(
    id = id,
    serverId = serverId,
    path = path,
    name = name,
    mediaType = mediaType,
    durationMs = durationMs,
    addedAt = addedAt,
)

fun PlaylistItem.toEntity(): PlaylistItemEntity = PlaylistItemEntity(
    id = id,
    serverId = serverId,
    path = path,
    name = name,
    mediaType = mediaType,
    durationMs = durationMs,
    addedAt = addedAt,
)
