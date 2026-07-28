package com.example.webdavplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/**
 * Media3 官方视频渲染组件（TextureView 模式，绑 MediaController / Player）。
 *
 * 经 [PlayerView] 把视频渲染到 [android.view.TextureView]；PlayerView 在设置 player 时
 * 会回调 [androidx.media3.common.SimpleBasePlayer] 的 surface 钩子，本应用已在
 * [com.example.webdavplayer.service.EngineMedia3Adapter] 中 override 这些钩子，
 * 把 TextureView 转发给 [com.example.webdavplayer.domain.repository.PlayerRepository.setVideoSurface]，
 * 从而让 Media3 引擎的视频帧直达单例引擎（与 VLC 的 TextureView 穿透路径殊途同归）。
 *
 * 控制条使用应用自己的 [PlayerControlBar]（故 `useController = false`）。
 */
@Composable
fun Media3PlayerSurface(
    player: Player?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false // 用应用自己的控制条
                setSurfaceType(PlayerView.SURFACE_TYPE_TEXTURE_VIEW)
            }
        },
        update = { view -> view.player = player },
    )
}
