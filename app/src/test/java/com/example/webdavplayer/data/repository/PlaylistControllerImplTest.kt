package com.example.webdavplayer.data.repository

import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PlaylistControllerImpl] 导航行为单元测试（§4.2 / §6 T08）。
 * 纯 Kotlin，使用假列表，不依赖 Android 框架。
 */
class PlaylistControllerImplTest {

    private lateinit var controller: PlaylistControllerImpl

    @Before
    fun setUp() {
        controller = PlaylistControllerImpl()
    }

    private fun item(id: String, name: String = id): PlaylistItem = PlaylistItem(
        id = id,
        serverId = "s1",
        path = "/$name",
        name = name,
        mediaType = MediaType.VIDEO,
        durationMs = 0L,
        addedAt = 0L,
    )

    private fun items(vararg ids: String): List<PlaylistItem> = ids.map { item(it) }

    // ===== 顺序模式 =====
    @Test
    fun sequential_next_advances_and_returnsNullAtEnd() {
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("B"))
        assertEquals("C", controller.next()?.id)
        assertNull("SEQUENTIAL: next() at end should be null", controller.next()?.id)
    }

    @Test
    fun sequential_previous_staysAtFirstWhenAtStart() {
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("A"))
        assertEquals("A", controller.previous()?.id) // 不回绕
        assertEquals("B", controller.next()?.id)
        assertEquals("A", controller.previous()?.id)
    }

    @Test
    fun sequential_next_fromNoCurrent_returnsFirst() {
        controller.sync(items("A", "B"))
        assertEquals("A", controller.next()?.id)
    }

    // ===== 循环模式 =====
    @Test
    fun loop_next_wrapsAround() {
        controller.setMode(PlayMode.LOOP)
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("A"))
        assertEquals("B", controller.next()?.id)
        assertEquals("C", controller.next()?.id)
        assertEquals("A", controller.next()?.id) // 回到开头
    }

    @Test
    fun loop_previous_wrapsBackward() {
        controller.setMode(PlayMode.LOOP)
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("A"))
        assertEquals("C", controller.previous()?.id) // 回绕到末尾
    }

    // ===== 随机模式 =====
    @Test
    fun shuffle_next_and_previous_returnItemsInList() {
        controller.setMode(PlayMode.SHUFFLE)
        val list = items("A", "B", "C", "D")
        controller.sync(list)

        val next = controller.next()
        val prev = controller.previous()

        // 随机模式下至少保证返回的是列表中的有效项（非空且属于列表）
        assertNotNull(next)
        assertNotNull(prev)
        assertTrue(next!! in list)
        assertTrue(prev!! in list)
    }

    // ===== onItemEnded =====
    @Test
    fun onItemEnded_sequential_advances_thenNull() {
        controller.sync(items("A", "B"))
        controller.setCurrent(item("A"))
        assertEquals("B", controller.onItemEnded()?.id)
        assertNull(controller.onItemEnded()?.id)
    }

    @Test
    fun onItemEnded_loop_wraps() {
        controller.setMode(PlayMode.LOOP)
        controller.sync(items("A", "B"))
        controller.setCurrent(item("B"))
        assertEquals("A", controller.onItemEnded()?.id)
    }

    // ===== current / setCurrent =====
    @Test
    fun current_isNullBeforeAnySelection() {
        controller.sync(items("A", "B"))
        assertNull(controller.current())
    }

    @Test
    fun setCurrent_selectsById() {
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("C"))
        assertEquals("C", controller.current()?.id)
    }

    @Test
    fun current_returnsNullWhenIndexNegative() {
        controller.sync(emptyList())
        assertNull(controller.current())
    }

    // ===== sync 保留当前项（按 id） =====
    @Test
    fun sync_preservesCurrentByItemId() {
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("B"))
        // 列表换位但保留同一 id 的当前项
        controller.sync(items("X", "B", "Y"))
        assertEquals("B", controller.current()?.id)
    }

    @Test
    fun sync_resetsCurrentWhenNoPreviousSelection() {
        controller.sync(items("A", "B"))
        controller.sync(items("X", "Y"))
        assertNull(controller.current())
    }

    // ===== 删除当前项时索引兜底（§一致性） =====
    @Test
    fun sync_afterDeletingCurrentItem_keepsAliveItemAtSameSlot() {
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("B")) // currentIndex = 1
        // 删除当前项 B → [A, C]，应保留同槽位存活项 C（而非失步为 -1）
        controller.sync(items("A", "C"))
        assertEquals("C", controller.current()?.id)
    }

    @Test
    fun sync_afterDeletingLastItem_keepsLastAvailable() {
        controller.sync(items("A", "B", "C"))
        controller.setCurrent(item("C")) // 末项 currentIndex = 2
        controller.sync(items("A", "B"))
        assertEquals("B", controller.current()?.id) // 兜底到末项
    }

    @Test
    fun sync_afterDeletingOnlyItem_currentBecomesNull() {
        controller.sync(items("A"))
        controller.setCurrent(item("A"))
        controller.sync(emptyList())
        assertNull(controller.current())
    }

    // ===== 空列表防御 =====
    @Test
    fun emptyList_nextAndPreviousReturnNull() {
        controller.sync(emptyList())
        assertNull(controller.next())
        assertNull(controller.previous())
    }
}
