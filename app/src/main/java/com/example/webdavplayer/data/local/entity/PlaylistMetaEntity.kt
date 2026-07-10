package com.example.webdavplayer.data.local.entity

import androidx.room.Entity
import com.example.webdavplayer.domain.model.PlayMode

/**
 * 播放模式元信息（C2 / §4.2）。
 * 与条目表解耦：单例行（id=0）保存当前播放模式（SEQUENTIAL/LOOP/SHUFFLE）。
 * 不改 [PlaylistItemEntity] 字段契约。
 */
@Entity(tableName = "playlist_meta")
data class PlaylistMetaEntity(
    @androidx.room.PrimaryKey val id: Int = 0,
    val mode: PlayMode,
)
