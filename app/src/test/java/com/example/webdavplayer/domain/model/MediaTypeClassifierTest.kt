package com.example.webdavplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [MediaTypeClassifier] 表驱动单元测试（§8：contentType 优先，扩展名兜底）。
 *
 * 覆盖：
 * - [MediaTypeClassifier.fromContentType]：video/audio 子类型 与 未知/null
 * - [MediaTypeClassifier.fromExtension]：视频/音频/其它扩展名白名单
 * - [MediaTypeClassifier.classify]：综合识别（contentType 优先）
 */
class MediaTypeClassifierTest {

    // ===== fromContentType 表 =====
    data class ContentTypeCase(val contentType: String?, val expected: MediaType?)

    @Test
    fun fromContentType_table() {
        val cases = listOf(
            ContentTypeCase("video/mp4", MediaType.VIDEO),
            ContentTypeCase("video/quicktime", MediaType.VIDEO),
            ContentTypeCase("VIDEO/MPEG", MediaType.VIDEO), // 忽略大小写
            ContentTypeCase("audio/mpeg", MediaType.AUDIO),
            ContentTypeCase("audio/MP3", MediaType.AUDIO),  // 忽略大小写
            ContentTypeCase("image/png", null),
            ContentTypeCase("application/octet-stream", null),
            ContentTypeCase("text/plain", null),
            ContentTypeCase("", null),
            ContentTypeCase(null, null),
        )
        cases.forEach { (ct, exp) ->
            assertEquals("fromContentType($ct)", exp, MediaTypeClassifier.fromContentType(ct))
        }
    }

    // ===== fromExtension 表 =====
    data class ExtensionCase(val name: String, val expected: MediaType?)

    @Test
    fun fromExtension_videoTable() {
        val videoNames = listOf(
            "movie.mp4", "clip.MKV", "film.avi", "vid.mov", "tv.ts",
            "rec.m2ts", "flash.flv", "web.webm", "win.wmv", "old.rmvb",
            "iphone.m4v", "dvd.mpg", "dvd2.mpeg",
        )
        videoNames.forEach { name ->
            assertEquals("fromExtension($name) should be VIDEO", MediaType.VIDEO, MediaTypeClassifier.fromExtension(name))
        }
    }

    @Test
    fun fromExtension_audioTable() {
        val audioNames = listOf(
            "song.mp3", "lossless.FLAC", "aac.aac", "ringtone.m4a",
            "pcm.wav", "voice.ogg", "win.wma", "talk.opus",
        )
        audioNames.forEach { name ->
            assertEquals("fromExtension($name) should be AUDIO", MediaType.AUDIO, MediaTypeClassifier.fromExtension(name))
        }
    }

    @Test
    fun fromExtension_otherTable() {
        val otherNames = listOf(
            "doc.pdf", "image.png", "archive.zip", "readme.txt",
            "noextension", ".hidden",
        )
        otherNames.forEach { name ->
            assertNull("fromExtension($name) should be null", MediaTypeClassifier.fromExtension(name))
        }
    }

    // ===== classify 综合识别表 =====
    data class ClassifyCase(
        val contentType: String?,
        val name: String,
        val expected: MediaType,
    )

    @Test
    fun classify_contentTypeWinsOverExtension() {
        // contentType=video/* 优先，即使扩展名是其它
        assertEquals(MediaType.VIDEO, MediaTypeClassifier.classify("video/mp4", "notes.txt"))
        assertEquals(MediaType.AUDIO, MediaTypeClassifier.classify("audio/mpeg", "clip.mp4"))
    }

    @Test
    fun classify_extensionBacksUpWhenContentTypeUnknown() {
        // contentType 未知/空 → 扩展名兜底
        assertEquals(MediaType.VIDEO, MediaTypeClassifier.classify("application/octet-stream", "movie.mp4"))
        assertEquals(MediaType.AUDIO, MediaTypeClassifier.classify(null, "song.mp3"))
        assertEquals(MediaType.VIDEO, MediaTypeClassifier.classify("", "film.mkv"))
    }

    @Test
    fun classify_otherWhenNothingMatches() {
        val cases = listOf(
            ClassifyCase(null, "readme.txt", MediaType.OTHER),
            ClassifyCase("application/octet-stream", "data.bin", MediaType.OTHER),
            ClassifyCase("", "image.png", MediaType.OTHER),
        )
        cases.forEach { (ct, name, exp) ->
            assertEquals("classify($ct, $name)", exp, MediaTypeClassifier.classify(ct, name))
        }
    }
}
