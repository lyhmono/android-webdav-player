package com.example.webdavplayer.domain.model

/**
 * 可播放媒体（传递给 PlayerEngine，§4.2）。
 *
 * @param uri 绝对流式地址（baseUrl + 规范化相对路径）
 * @param headers 流式请求头（Basic 鉴权头由上层预置；Digest 由共享 OkHttp 拦截器处理）
 * @param name 展示名
 * @param mediaType 媒体类型
 * @param serverId 归属服务器（用于取回共享 OkHttp 客户端做流式播放）
 * @param trustSelfSigned 是否跳过 TLS 校验（仅 libVLC 内核需要此开关）
 * @param subtitles 外部字幕轨列表（P2）。默认空；非空时由内核以外部文本轨加载，运行时可选。
 */
data class PlayableMedia(
    val uri: String,
    val headers: Map<String, String>,
    val name: String,
    val mediaType: MediaType,
    val serverId: String,
    val trustSelfSigned: Boolean = false,
    val subtitles: List<SubtitleTrack> = emptyList(),
)
