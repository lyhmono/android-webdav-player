package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.SubtitleTrack

/**
 * 将播放列表项解析为可播放媒体（构建流式 URI + 鉴权头 + TLS 开关）。
 *
 * 接口定义在领域层；实现在 data 层（需 OkHttp 鉴权头与路径规范化）。
 * 这样领域层无需直接依赖 OkHttp / WebDavPath（§8 分层契约）。
 */
interface MediaResolver {
    suspend fun resolve(item: PlaylistItem): PlayableMedia

    /**
     * 发现主媒体的同级字幕文件（同名前缀的 .srt/.vtt/.ass），返回可加载的字幕轨。
     * 用于 P2「字幕加载与选择」：字幕与主媒体走同一服务器鉴权，流式加载，不落本地。
     * 发现失败（离线 / 无权限）时返回空列表，不影响主媒体播放。
     */
    suspend fun discoverSubtitles(item: PlaylistItem): List<SubtitleTrack>
}
