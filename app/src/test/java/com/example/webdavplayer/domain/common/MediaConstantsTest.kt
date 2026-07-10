package com.example.webdavplayer.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MediaConstants] 扩展名提取与白名单的单元测试（§8 约定）。
 */
class MediaConstantsTest {

    @Test
    fun extensionOf_lowercases_and_strips_dot() {
        assertEquals("mp4", MediaConstants.extensionOf("movie.MP4"))
        assertEquals("mkv", MediaConstants.extensionOf("clip.mkv"))
        assertEquals("mp3", MediaConstants.extensionOf("song.MP3"))
    }

    @Test
    fun extensionOf_handles_no_extension() {
        assertEquals("", MediaConstants.extensionOf("README"))
        assertEquals("", MediaConstants.extensionOf(""))
    }

    @Test
    fun extensionOf_handles_dotfile() {
        // ".gitignore" 的 "扩展名" 视为 gitignore（按 substringAfterLast('.') 语义）
        assertEquals("gitignore", MediaConstants.extensionOf(".gitignore"))
    }

    @Test
    fun extensionOf_handles_multiple_dots() {
        assertEquals("tar.gz", MediaConstants.extensionOf("backup.tar.gz"))
    }

    @Test
    fun videoExtensions_whitelist_matches_spec() {
        val expected = setOf(
            "mp4", "mkv", "avi", "mov", "ts", "m2ts",
            "flv", "webm", "wmv", "rmvb", "m4v", "mpg", "mpeg",
        )
        assertEquals(expected, MediaConstants.VIDEO_EXTENSIONS)
        expected.forEach { assertTrue("$it should be a video extension", it in MediaConstants.VIDEO_EXTENSIONS) }
    }

    @Test
    fun audioExtensions_whitelist_matches_spec() {
        val expected = setOf(
            "mp3", "flac", "m4a", "aac", "wav", "ogg", "wma", "opus",
        )
        assertEquals(expected, MediaConstants.AUDIO_EXTENSIONS)
        expected.forEach { assertTrue("$it should be an audio extension", it in MediaConstants.AUDIO_EXTENSIONS) }
    }

    @Test
    fun video_and_audio_whitelists_do_not_overlap() {
        val overlap = MediaConstants.VIDEO_EXTENSIONS.intersect(MediaConstants.AUDIO_EXTENSIONS)
        assertTrue("video/audio extension sets must be disjoint", overlap.isEmpty())
    }
}
