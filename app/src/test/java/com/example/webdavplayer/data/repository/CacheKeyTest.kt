package com.example.webdavplayer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [sha256Hex] 缓存键单元测试（对应工程师修复 #C）。
 *
 * 校验：
 * - 确定性：同一输入多次调用结果一致；
 * - 不同路径产出不同键（避免 [String.hashCode] 可能的碰撞导致缓存互相覆盖）；
 * - 输出为 64 位十六进制（SHA-256）。
 */
class CacheKeyTest {

    @Test
    fun sha256Hex_isDeterministic() {
        val a = sha256Hex("/videos/clip.mp4")
        val b = sha256Hex("/videos/clip.mp4")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }

    @Test
    fun sha256Hex_distinctPaths_produceDistinctKeys() {
        val x = sha256Hex("/videos/a.mp4")
        val y = sha256Hex("/videos/b.mp4")
        val z = sha256Hex("/music/a.mp4")
        assertNotEquals(x, y)
        assertNotEquals(x, z)
        assertNotEquals(y, z)
    }

    @Test
    fun sha256Hex_normalizedVsRaw_differentInputs_distinctKeys() {
        // 即使仅大小写/层级不同，也应得到稳定且互异的键。
        val a = sha256Hex("/a/b.mp4")
        val b = sha256Hex("/a//b.mp4")
        assertNotEquals(a, b)
    }
}
