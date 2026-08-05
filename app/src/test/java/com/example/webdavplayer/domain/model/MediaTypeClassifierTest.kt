package com.example.webdavplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [MediaTypeClassifier] 表驱动单元测试（§8：contentType 优先，扩展名兜底）。
 *
 * 覆盖：
 * - [MediaTypeClassifier.fromContentType]：video/image 子类型 与 未知/null
 * - [MediaTypeClassifier.fromExtension]：视频/图片/其它扩展名白名单
 * - [MediaTypeClassifier.classify]：综合识别（contentType 优先）
 *
 * 注：音频不再作为独立类型支持（产品方向聚焦视频+图片），
 * 原 audio/* contentType 与 mp3/flac 等扩展名现归为 null / OTHER。
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
            ContentTypeCase("image/png", MediaType.IMAGE),
            ContentTypeCase("image/jpeg", MediaType.IMAGE),
            ContentTypeCase("application/octet-stream", null),
            ContentTypeCase("text/plain", null),
            ContentTypeCase("audio/mpeg", null), // 音频不再独立分类
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
    fun fromExtension_audioFallsToNull() {
        // 音频扩展名已从白名单移除——识别为 null（即归类为 OTHER）
        val audioNames = listOf(
            "song.mp3", "lossless.FLAC", "aac.aac", "ringtone.m4a",
            "pcm.wav", "voice.ogg", "win.wma", "talk.opus",
        )
        audioNames.forEach { name ->
            assertNull("fromExtension($name) should be null (audio dropped)", MediaTypeClassifier.fromExtension(name))
        }
    }

    @Test
    fun fromExtension_imageTable() {
        val imageNames = listOf(
            "photo.jpg", "pic.JPEG", "shot.png", "anim.gif", "web.webp",
            "scan.bmp", "img.heic", "art.heif", "bg.avif", "icon.svg",
        )
        imageNames.forEach { name ->
            assertEquals("fromExtension($name) should be IMAGE", MediaType.IMAGE, MediaTypeClassifier.fromExtension(name))
        }
    }

    @Test
    fun fromExtension_otherTable() {
        val otherNames = listOf(
            "doc.pdf", "archive.zip", "readme.txt",
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
        // audio/* 不再独立分类 → fallback 到扩展名 → clip.mp4 = VIDEO
        assertEquals(MediaType.VIDEO, MediaTypeClassifier.classify("audio/mpeg", "clip.mp4"))
    }

    @Test
    fun classify_extensionBacksUpWhenContentTypeUnknown() {
        // contentType 未知/空 → 扩展名兜底
        assertEquals(MediaType.VIDEO, MediaTypeClassifier.classify("application/octet-stream", "movie.mp4"))
        // 音频扩展名不再识别 → song.mp3 = OTHER
        assertEquals(MediaType.OTHER, MediaTypeClassifier.classify(null, "song.mp3"))
        assertEquals(MediaType.VIDEO, MediaTypeClassifier.classify("", "film.mkv"))
    }

    @Test
    fun classify_otherWhenNothingMatches() {
        val cases = listOf(
            ClassifyCase(null, "readme.txt", MediaType.OTHER),
            ClassifyCase("application/octet-stream", "data.bin", MediaType.OTHER),
        )
        cases.forEach { (ct, name, exp) ->
            assertEquals("classify($ct, $name)", exp, MediaTypeClassifier.classify(ct, name))
        }
    }

    @Test
    fun classify_imageExtensionBacksUp() {
        // contentType 未知/空 + 图片扩展名 → IMAGE
        assertEquals(MediaType.IMAGE, MediaTypeClassifier.classify(null, "photo.png"))
        assertEquals(MediaType.IMAGE, MediaTypeClassifier.classify("application/octet-stream", "pic.jpg"))
    }
}
