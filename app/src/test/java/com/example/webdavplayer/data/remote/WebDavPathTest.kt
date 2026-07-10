package com.example.webdavplayer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [WebDavPath] 单元测试（§8 共享约定）。
 *
 * 纯 Kotlin / 纯 JVM 可运行（无 Robolectric、无 Android 运行时）：
 * T1（C6）已将 [join] 的 `android.net.Uri.encode` 替换为 `java.net.URLEncoder`，
 * 去除 Android 依赖，因此 [join] 用例在普通 JVM 单测下直接通过（PRD C6 AC2）。
 *
 * 编码语义（与 P0 §8 约定一致）：
 * - RFC 3986 非保留字符 `-_.~` 不被编码（`~` 由实现显式从 `%7E` 还原）；
 * - 中文/空格按 UTF-8 百分号编码，空格 `+` → `%20`；
 * - 其余特殊字符（`& = ? # +`）仍按百分号编码；
 * - `*` 位于 URLEncoder 安全集内，保持字面量（PRD §11.2 已确认该行为，且 `*`
 *   属 RFC 3986 sub-delims，作为路径段合法）。
 */
class WebDavPathTest {

    // ===== normalize =====
    @Test
    fun normalize_stripsTrailingSlash() {
        assertEquals("/a/b", WebDavPath.normalize("/a/b/"))
        assertEquals("/a/b", WebDavPath.normalize("/a/b///"))
    }

    @Test
    fun normalize_collapsesMultipleSlashes() {
        assertEquals("/a/b", WebDavPath.normalize("//a//b//"))
    }

    @Test
    fun normalize_rootStaysSlash() {
        assertEquals("/", WebDavPath.normalize("/"))
        assertEquals("/", WebDavPath.normalize("///"))
    }

    @Test
    fun normalize_trimsAndConvertsBackslash() {
        assertEquals("/a/b", WebDavPath.normalize("  /a/b  "))
        assertEquals("/a/b", WebDavPath.normalize("\\a\\b\\"))
    }

    @Test
    fun normalize_relativeNoLeadingSlash() {
        assertEquals("a/b", WebDavPath.normalize("a/b"))
    }

    // ===== parentOf =====
    @Test
    fun parentOf_basic() {
        assertEquals("/a", WebDavPath.parentOf("/a/b.mp4"))
        assertEquals("/", WebDavPath.parentOf("/a"))
        assertEquals("/", WebDavPath.parentOf("/"))
    }

    @Test
    fun parentOf_relativePath() {
        assertEquals("a", WebDavPath.parentOf("a/b"))
    }

    // ===== nameOf =====
    @Test
    fun nameOf_basic() {
        assertEquals("b.mp4", WebDavPath.nameOf("/a/b.mp4"))
        assertEquals("", WebDavPath.nameOf("/"))
        assertEquals("", WebDavPath.nameOf(""))
        assertEquals("name.mp4", WebDavPath.nameOf("name.mp4"))
    }

    // ===== join（纯 JVM，T1 修复后无需 Robolectric） =====
    @Test
    fun join_simpleRelative() {
        assertEquals("http://h/a/b.mp4", WebDavPath.join("http://h", "/a/b.mp4"))
    }

    @Test
    fun join_stripsBaseTrailingSlash() {
        assertEquals("http://h/a/b.mp4", WebDavPath.join("http://h/", "a/b.mp4"))
    }

    @Test
    fun join_encodesChineseAndSpace() {
        assertEquals(
            "http://h/sub/%E7%94%B5%E5%BD%B1.mp4",
            WebDavPath.join("http://h", "sub/电影.mp4"),
        )
    }

    @Test
    fun join_encodesSpaceInSegment() {
        assertEquals("http://h/my%20movie.mp4", WebDavPath.join("http://h", "my movie.mp4"))
    }

    @Test
    fun join_emptyRelativeYieldsBaseWithSlash() {
        assertEquals("http://h/", WebDavPath.join("http://h", ""))
        assertEquals("http://h/", WebDavPath.join("http://h", "/"))
    }

    @Test
    fun join_normalizesBackslash() {
        assertEquals("http://h/a/b", WebDavPath.join("http://h", "\\a\\b"))
    }

    // ===== join：RFC 3986 非保留字符 `-_.~` 不被编码（C6） =====
    @Test
    fun join_preservesUnreservedHyphenUnderscoreDotTilde() {
        // - _ . 本就不在 URLEncoder 编码集；~ 由实现显式还原（%7E → ~）。
        assertEquals("http://h/a-b_c.d~e", WebDavPath.join("http://h", "a-b_c.d~e"))
    }

    @Test
    fun join_restoresTildeEncodedByUrleocoder() {
        assertEquals("http://h/x~y", WebDavPath.join("http://h", "x~y"))
    }

    @Test
    fun join_encodesReservedSpecialChars() {
        // & = ? # 不在非保留集，须百分号编码。
        assertEquals("http://h/a%26b%3Dc%3Fd%23e", WebDavPath.join("http://h", "a&b=c?d#e"))
    }

    @Test
    fun join_keepsAsteriskLiteral_urleocoderSafeSet() {
        // * 位于 URLEncoder 安全集，保持字面量（PRD §11.2 确认，且属 RFC3986 sub-delims）。
        assertEquals("http://h/a*b", WebDavPath.join("http://h", "a*b"))
    }

    @Test
    fun join_encodesPlusSign() {
        // 字面 + 须编码为 %2B（URLEncoder 会把 + 视为空格，故实现需显式编码）。
        assertEquals("http://h/a%2Bb", WebDavPath.join("http://h", "a+b"))
    }

    @Test
    fun join_mixedChineseAndUnreservedAndSpecial() {
        assertEquals(
            "http://h/%E7%94%B5%E5%BD%B1-a_b*c%23.mp4",
            WebDavPath.join("http://h", "电影-a_b*c#.mp4"),
        )
    }
}
