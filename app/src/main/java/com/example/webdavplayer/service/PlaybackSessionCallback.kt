package com.example.webdavplayer.service

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController

/**
 * MediaSession 回调（C1 / §7）。
 *
 * 主要处理“从媒体项 id 直接播放”这类会话级意图（如锁屏/通知点击某一曲目、
 * 或 [android.media.MediaMetadata] 的 mediaId）。实际播放仍委托给服务侧的统一
 * [playItem]（走 [com.example.webdavplayer.domain.usecase.PlayMediaUseCase]）。
 */
@UnstableApi
class PlaybackSessionCallback(
    private val playlistController: PlaylistController,
    private val playItem: (PlaylistItem) -> Unit,
) : MediaSession.Callback() {

    override fun onPlayFromMediaId(
        mediaSession: MediaSession,
        mediaId: String,
        extras: Bundle?,
    ) {
        val item = playlistController.snapshot().firstOrNull { it.id == mediaId } ?: return
        playItem(item)
    }
}
