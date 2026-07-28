package com.example.webdavplayer.domain.common

import com.example.webdavplayer.domain.model.MediaType

/**
 * 文件展示格式化工具（§1.3 UI 增强）。
 *
 * 纯函数，无 Android 依赖，可直接 JVM 单测。
 * 提供文件大小人类可读化与媒体类型中文标签。
 */
object FileFormatter {

    /** 字节 → 人类可读字符串（如 "1.5 MB"、"800 KB"、"< 1 KB"）。 */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "未知大小"
        if (bytes < 1024) return "${bytes} B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return "%.1f %s".format(value, units[unitIndex])
    }

    /** 媒体类型 → 中文标签。 */
    fun mediaTypeLabel(mediaType: MediaType): String = when (mediaType) {
        MediaType.VIDEO -> "视频"
        MediaType.AUDIO -> "音频"
        MediaType.OTHER -> "文件"
    }
}
