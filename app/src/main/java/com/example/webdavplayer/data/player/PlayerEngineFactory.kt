package com.example.webdavplayer.data.player

import android.content.Context
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.player.PlayerEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放内核工厂（§4.2）。
 *
 * 简化版：仅 Media3/ExoPlayer 内核。VLC 已移除。
 */
@Singleton
open class PlayerEngineFactory @Inject constructor(
    private val streamingSource: WebDavStreamingSource,
) {
    open fun create(type: EngineType, context: Context): PlayerEngine =
        ExoPlayerEngine(context, streamingSource)
}