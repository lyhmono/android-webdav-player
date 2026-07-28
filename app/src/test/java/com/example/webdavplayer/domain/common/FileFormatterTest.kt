package com.example.webdavplayer.domain.common

import com.example.webdavplayer.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FileFormatter] 单元测试（纯 JVM）。
 * 锁定文件大小格式化与媒体类型中文标签的不变量。
 */
class FileFormatterTest {

    @Test
    fun formatSize_bytes() {
        assertEquals("0 B", FileFormatter.formatSize(0))
        assertEquals("512 B", FileFormatter.formatSize(512))
        assertEquals("1023 B", FileFormatter.formatSize(1023))
    }

    @Test
    fun formatSize_kilobytes() {
        assertEquals("1.0 KB", FileFormatter.formatSize(1024))
        assertEquals("1.5 KB", FileFormatter.formatSize(1536))
    }

    @Test
    fun formatSize_megabytes() {
        assertEquals("1.0 MB", FileFormatter.formatSize(1024 * 1024L))
        assertEquals("1.5 MB", FileFormatter.formatSize(1024 * 1024L * 3 / 2))
    }

    @Test
    fun formatSize_gigabytes() {
        assertEquals("1.0 GB", FileFormatter.formatSize(1024 * 1024 * 1024L))
    }

    @Test
    fun formatSize_negative() {
        assertEquals("未知大小", FileFormatter.formatSize(-1))
    }

    @Test
    fun mediaTypeLabel_correct() {
        assertEquals("视频", FileFormatter.mediaTypeLabel(MediaType.VIDEO))
        assertEquals("音频", FileFormatter.mediaTypeLabel(MediaType.AUDIO))
        assertEquals("文件", FileFormatter.mediaTypeLabel(MediaType.OTHER))
    }
}
