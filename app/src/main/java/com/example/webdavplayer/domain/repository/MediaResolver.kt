package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaylistItem

/**
 * 将播放列表项解析为可播放媒体（构建流式 URI + 鉴权头 + TLS 开关）。
 *
 * 接口定义在领域层；实现在 data 层（需 OkHttp 鉴权头与路径规范化）。
 * 这样领域层无需直接依赖 OkHttp / WebDavPath（§8 分层契约）。
 */
interface MediaResolver {
    suspend fun resolve(item: PlaylistItem): PlayableMedia
}
