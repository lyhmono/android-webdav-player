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

    /** 字幕扩展名白名单（小写，无点）。 */
    val SUBTITLE_EXTENSIONS: Set<String> = setOf("srt", "vtt", "ass", "ssa", "sub")

    /** 是否为字幕文件（按扩展名判定）。 */
    fun isSubtitleFile(name: String): Boolean =
        extensionOf(name) in SUBTITLE_EXTENSIONS

    /** 字幕扩展名 → MIME 类型（best-effort 兜底为 SRT）。 */
    fun subtitleMimeType(name: String): String = when (extensionOf(name)) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip" // srt / sub / 未知
    }

    /**
     * 从字幕文件名推断语言代码：形如 `base.zh.srt` → "zh"，`base.srt` → null。
     * 取去扩展名后最后一段，若为 2~5 位纯字母则视为语言代码。
     */
    fun subtitleLanguageFromName(name: String): String? {
        val core = name.substringBeforeLast('.') // 去掉末段扩展名
        val parts = core.split('.')
        if (parts.size < 2) return null
        val cand = parts.last()
        return if (cand.length in 2..5 && cand.all { it.isLetter() }) cand else null
    }

    /**
     * 字幕是否与主媒体同根（同目录、同名前缀）：
     * `foo.zh.srt` / `foo.srt` 匹配主媒体 `foo.mp4`；`bar.srt` 不匹配。
     */
    fun isSiblingSubtitle(name: String, mediaBaseName: String): Boolean {
        if (!isSubtitleFile(name)) return false
        val noExt = name.substringBeforeLast('.') // "foo.zh"
        return noExt == mediaBaseName || noExt.substringBeforeLast('.') == mediaBaseName
    }
}
