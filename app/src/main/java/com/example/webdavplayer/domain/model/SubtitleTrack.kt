package com.example.webdavplayer.domain.model

/**
 * 外部字幕轨（P2：字幕加载与选择）。
 *
 * @param uri 字幕文件绝对地址：WebDAV 的 http(s) URL（复用主媒体共享 OkHttp 鉴权加载），
 *            或本地 file:// 路径（离线/缓存场景）。
 * @param mimeType 字幕 MIME（application/x-subrip / text/vtt / text/x-ssa）。
 * @param language 语言代码（"zh" / "en" / null），用于运行时选择与展示。
 * @param label 展示名（通常取自文件名）。
 */
data class SubtitleTrack(
    val uri: String,
    val mimeType: String,
    val language: String?,
    val label: String,
)
