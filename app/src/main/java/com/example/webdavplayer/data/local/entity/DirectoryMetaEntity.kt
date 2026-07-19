package com.example.webdavplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 目录级缓存元数据（§1.3 优化：缓存优先 + TTL 条件刷新）。
 *
 * 记录每个 `(serverId, parentPath)` 最后一次成功从服务器刷新（PROPFIND）的时间戳，
 * 用于判断缓存是否仍然新鲜，避免每次进入目录都打整目录 PROPFIND，强化大目录性能。
 *
 * 主键约定：`id = "$serverId::$parentPath"`。前提是 `serverId` 不含 `::`
 * （由 [com.example.webdavplayer.domain.repository.ServerRepository] 生成，现为 UUID/时间戳，满足此前提）。
 */
@Entity(tableName = "directory_meta")
data class DirectoryMetaEntity(
    /** 复合主键：`"$serverId::$parentPath"`，保证同服务器同目录唯一（前提：serverId 不含 `::`）。 */
    @PrimaryKey val id: String,
    val serverId: String,
    val parentPath: String,
    /** 最后一次成功刷新的时间（[System.currentTimeMillis]）。 */
    val lastRefreshedAt: Long,
)
