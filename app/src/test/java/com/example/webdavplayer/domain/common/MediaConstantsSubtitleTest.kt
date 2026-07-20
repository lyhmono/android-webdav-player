package com.example.webdavplayer.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MediaConstants] 字幕识别 / 语言推断 / MIME 映射 的单元测试（P2）。
 */
class MediaConstantsSubtitleTest {

    @Test
    fun isSubtitleFile_matches_whitelist() {
        assertTrue(MediaConstants.isSubtitleFile("foo.srt"))
        assertTrue(MediaConstants.isSubtitleFile("foo.vtt"))
        assertTrue(MediaConstants.isSubtitleFile("foo.ass"))
        assertTrue(MediaConstants.isSubtitleFile("foo.ssa"))
        assertTrue(MediaConstants.isSubtitleFile("foo.sub"))
        assertTrue(MediaConstants.isSubtitleFile("FOO.SRT"))
    }

    @Test
    fun isSubtitleFile_rejects_media() {
        assertFalse(MediaConstants.isSubtitleFile("foo.mp4"))
        assertFalse(MediaConstants.isSubtitleFile("foo.mkv"))
        assertFalse(MediaConstants.isSubtitleFile("foo.nfo"))
    }

    @Test
    fun subtitleMimeType_maps_extensions() {
        assertEquals("text/vtt", MediaConstants.subtitleMimeType("foo.vtt"))
        assertEquals("text/x-ssa", MediaConstants.subtitleMimeType("foo.ass"))
        assertEquals("text/x-ssa", MediaConstants.subtitleMimeType("foo.ssa"))
        assertEquals("application/x-subrip", MediaConstants.subtitleMimeType("foo.srt"))
        assertEquals("application/x-subrip", MediaConstants.subtitleMimeType("foo.sub"))
        assertEquals("application/x-subrip", MediaConstants.subtitleMimeType("foo.unknown"))
    }

    @Test
    fun subtitleLanguageFromName_infers_language_suffix() {
        assertEquals("zh", MediaConstants.subtitleLanguageFromName("foo.zh.srt"))
        assertEquals("en", MediaConstants.subtitleLanguageFromName("foo.en.ass"))
        assertEquals("zh", MediaConstants.subtitleLanguageFromName("Movie.2009.zh.srt"))
    }

    @Test
    fun subtitleLanguageFromName_returns_null_without_lang() {
        assertEquals(null, MediaConstants.subtitleLanguageFromName("foo.srt"))
        assertEquals(null, MediaConstants.subtitleLanguageFromName("foo.1080p.srt")) // 含数字
        assertEquals(null, MediaConstants.subtitleLanguageFromName("foo.zh-CN.srt"))  // 含连字符
    }

    @Test
    fun isSiblingSubtitle_matches_same_base() {
        assertTrue(MediaConstants.isSiblingSubtitle("foo.srt", "foo"))
        assertTrue(MediaConstants.isSiblingSubtitle("foo.zh.srt", "foo"))
        assertTrue(MediaConstants.isSiblingSubtitle("foo.en.srt", "foo"))
        assertTrue(MediaConstants.isSiblingSubtitle("foo.ass", "foo"))
        // 带分辨率标签的主媒体也能匹配
        assertTrue(MediaConstants.isSiblingSubtitle("foo.1080p.zh.srt", "foo.1080p"))
    }

    @Test
    fun isSiblingSubtitle_rejects_other_base_or_non_sub() {
        assertFalse(MediaConstants.isSiblingSubtitle("bar.srt", "foo"))
        assertFalse(MediaConstants.isSiblingSubtitle("foo.mp4", "foo"))
        assertFalse(MediaConstants.isSiblingSubtitle("foo.zh.mp4", "foo"))
    }
}
