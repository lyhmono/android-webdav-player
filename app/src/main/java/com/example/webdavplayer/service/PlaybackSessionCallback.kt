package com.example.webdavplayer.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * MediaSession 回调（C1 / §7）。
 *
 * Media3 1.5.1 起 [MediaSession.Callback] 为接口且已移除 `onPlayFromMediaId` 等旧回调，
 * 播放控制统一经 [MediaSession] 默认实现与 [MediaController] 命令驱动。此处使用默认实现，
 * 实际的播放/暂停/上下首由 [com.example.webdavplayer.service.EngineMedia3Adapter] 与
 * 服务侧的 [com.example.webdavplayer.domain.usecase.PlayMediaUseCase] 处理。
 */
@UnstableApi
class PlaybackSessionCallback : MediaSession.Callback
