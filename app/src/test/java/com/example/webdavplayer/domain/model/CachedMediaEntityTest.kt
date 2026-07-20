package com.example.webdavplayer.domain.model

import com.example.webdavplayer.data.local.entity.CachedMediaEntity
import com.example.webdavplayer.data.local.entity.toDomain
import com.example.webdavplayer.data.local.entity.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CachedMedia] 实体 ↔ 领域模型 互转 单元测试。
 *
 * 离线缓存的核心下载/存储逻辑依赖 Android Context 与文件 I/O，
 * 纯 JVM 环境下仅验证实体映射的正确性。
 */
class CachedMediaEntityTest {

    @Test
    fun toEntity_toDomain_roundTrip() {
        val domain = CachedMedia(
            id = "s1:/videos/a.mp4",
            serverId = "s1",
            path = "/videos/a.mp4",
            name = "a.mp4",
            size = 1024 * 1024,
            downloadedAt = 1700000000000L,
        )
        val entity = domain.toEntity()
        val back = entity.toDomain()
        assertEquals(domain, back)
    }

    @Test
    fun entityDefaults() {
        val entity = CachedMediaEntity(
            id = "s1:/videos/a.mp4",
            serverId = "s1",
            path = "/videos/a.mp4",
            name = "a.mp4",
            size = 0,
            downloadedAt = 0,
        )
        assertEquals("s1", entity.serverId)
        assertEquals(0, entity.size)
    }
}
