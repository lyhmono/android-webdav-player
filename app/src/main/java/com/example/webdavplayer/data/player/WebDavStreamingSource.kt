package com.example.webdavplayer.data.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.webdavplayer.domain.model.PlayableMedia
import okhttp3.OkHttpClient

/**
 * 流式播放数据源（§8 共享约定）。
 *
 * 复用 [WebDavClient] 的 OkHttp（含自签信任 + Basic/Digest 鉴权）构建
 * ExoPlayer 的 [OkHttpDataSource]，实现「边下边播、不整文件下载」。
 */
class WebDavStreamingSource {
    fun createExoMediaSource(client: OkHttpClient, media: PlayableMedia): MediaSource {
        val factory = OkHttpDataSource.Factory(client)
            .setDefaultRequestProperties(media.headers)
        return ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(media.uri))
    }
}
