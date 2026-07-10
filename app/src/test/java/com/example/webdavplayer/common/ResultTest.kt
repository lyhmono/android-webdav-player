package com.example.webdavplayer.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Result] 封装的单元测试（§8 共享约定）。
 * 覆盖 Success/Error 的判定、取值与 runCatching 异常捕获。
 */
class ResultTest {

    @Test
    fun success_isSuccess_and_getOrNull_returnsData() {
        val r: Result<Int> = Result.success(42)
        assertTrue(r.isSuccess)
        assertFalse(r.isError)
        assertEquals(42, r.getOrNull())
    }

    @Test
    fun success_getOrThrow_returnsData() {
        val r: Result<String> = Result.success("hello")
        assertEquals("hello", r.getOrThrow())
    }

    @Test
    fun error_isError_and_getOrNull_isNull() {
        val ex = RuntimeException("boom")
        val r: Result<Int> = Result.error(ex)
        assertTrue(r.isError)
        assertFalse(r.isSuccess)
        assertNull(r.getOrNull())
    }

    @Test(expected = RuntimeException::class)
    fun error_getOrThrow_rethrows() {
        val ex = RuntimeException("boom")
        val r: Result<Int> = Result.error(ex)
        r.getOrThrow()
    }

    @Test
    fun error_holds_original_throwable() {
        val ex = IllegalStateException("state")
        val r: Result<Any> = Result.error(ex)
        // 通过 getOrThrow 抛出的应当是同一个实例
        try {
            r.getOrThrow()
        } catch (t: Throwable) {
            assertSame(ex, t)
        }
    }

    @Test
    fun runCatching_returnsSuccess_onNormalBlock() {
        val r: Result<Int> = Result.runCatching { 1 + 2 }
        assertTrue(r.isSuccess)
        assertEquals(3, r.getOrNull())
    }

    @Test
    fun runCatching_returnsError_onThrowingBlock() {
        val r: Result<Int> = Result.runCatching { throw IllegalArgumentException("bad") }
        assertTrue(r.isError)
    }

    @Test
    fun runCatching_error_preservesExceptionType() {
        val r: Result<Int> = Result.runCatching { throw IllegalArgumentException("bad") }
        assertTrue(r.isError)
        val err = r as Result.Error
        assertTrue(err.throwable is IllegalArgumentException)
    }

    @Test
    fun success_withNullData_isStillSuccess() {
        val r: Result<Int?> = Result.success(null)
        assertTrue(r.isSuccess)
        assertNull(r.getOrNull())
    }
}
