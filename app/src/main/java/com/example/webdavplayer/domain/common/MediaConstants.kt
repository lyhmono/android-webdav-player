package com.example.webdavplayer.domain.common

/**
 * 媒体类型识别共享常量（§8：视频白名单集中常量，避免散落）。
 * 视频扩展名以 PRD/技术方案锁定的白名单为准。
 */
object MediaConstants {
    /** 视频扩展名白名单（小写，无点）。 */
    val VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "mkv", "avi", "mov", "ts", "m2ts",
        "flv", "webm", "wmv", "rmvb", "m4v", "mpg", "mpeg",
    )

    /** 音频扩展名白名单（小写，无点）。 */
    val AUDIO_EXTENSIONS: Set<String> = setOf(
        "mp3", "flac", "m4a", "aac", "wav", "ogg", "wma", "opus",
    )

    /** 由文件名取小写扩展名（无点）。 */
    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()
}
