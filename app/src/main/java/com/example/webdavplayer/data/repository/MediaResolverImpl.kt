package com.example.webdavplayer.data.repository

import com.example.webdavplayer.data.remote.WebDavPath
import com.example.webdavplayer.domain.model.AuthType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.ServerRepository
import okhttp3.Credentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放列表项 → 可播放媒体 解析实现（data 层）。
 *
 * 构建流式 URI（baseUrl + 规范化路径）、Basic 鉴权头（Digest 由共享 OkHttp 拦截器处理）、
 * 以及 libVLC 所需的 TLS 跳过开关（§8）。
 */
@Singleton
class MediaResolverImpl @Inject constructor(
    private val serverRepository: ServerRepository,
) : MediaResolver {

    override suspend fun resolve(item: PlaylistItem): PlayableMedia {
        val cfg = serverRepository.getById(item.serverId)
            ?: throw IllegalStateException("找不到服务器：${item.serverId}")
        val uri = WebDavPath.join(cfg.baseUrl, item.path)
        val headers = if (cfg.authType == AuthType.BASIC) {
            mapOf("Authorization" to Credentials.basic(cfg.username, cfg.encryptedPassword))
        } else {
            emptyMap()
        }
        return PlayableMedia(
            uri = uri,
            headers = headers,
            name = item.name,
            mediaType = item.mediaType,
            serverId = item.serverId,
            trustSelfSigned = cfg.trustSelfSigned,
        )
    }
}
