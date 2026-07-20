package com.example.webdavplayer.data.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.example.webdavplayer.domain.model.PlayableMedia
import okhttp3.OkHttpClient

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 流式播放数据源（§8 共享约定）。
 *
 * 复用 [WebDavClient] 的 OkHttp（含自签信任 + Basic/Digest 鉴权）构建
 * ExoPlayer 的 [OkHttpDataSource]，实现「边下边播、不整文件下载」。
 *
 * P2 字幕：若 [PlayableMedia.subtitles] 非空，将每条外部字幕作为
 * [SingleSampleMediaSource]（同源 OkHttp 鉴权）与主媒体经 [MergingMediaSource] 合并，
 * 使字幕随主媒体一起流式加载，无需落本地、且继承同一套鉴权。
 */
@UnstableApi
@Singleton
class WebDavStreamingSource @Inject constructor() {
    fun createExoMediaSource(client: OkHttpClient, media: PlayableMedia): MediaSource {
        val factory = OkHttpDataSource.Factory(client)
            .setDefaultRequestProperties(media.headers)
        val main = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(media.uri))
        if (media.subtitles.isEmpty()) return main

        val subSources = media.subtitles.map { sub ->
            val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .build()
            SingleSampleMediaSource.Factory(factory)
                .createMediaSource(config, C.TIME_UNSET)
        }
        return MergingMediaSource(main, *subSources.toTypedArray())
    }
}
