package com.example.webdavplayer.ui.player

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 承载视频渲染的 [TextureView]（穿透抽象层直达内核）。
 *
 * 通过 [TextureView.SurfaceTextureListener] 把 SurfaceTexture 生命周期回调给上层：
 * - [TextureView.SurfaceTextureListener.onSurfaceTextureAvailable] → [onSurfaceReady]（绑定到引擎）；
 * - [TextureView.SurfaceTextureListener.onSurfaceTextureDestroyed] → [onSurfaceDestroyed]（解绑）。
 *
 * 使用 TextureView（而非 SurfaceView）是因为 libVLC 的 `IVLCVout.setVideoSurface` 只接受
 * `(Surface, SurfaceHolder)` 或 `(SurfaceTexture)`，没有「裸 Surface 单参」重载；统一用 TextureView
 * 可同时满足 Media3（把 SurfaceTexture 包成 Surface）与 libVLC（直接 `setVideoView(TextureView)`）。
 *
 * @param modifier 外层布局修饰（在 PlayerScreen 中传入视频区 Modifier）。
 * @param onSurfaceReady 视频视图就绪（创建）时回调，参数为可用的 [TextureView]。
 * @param onSurfaceDestroyed 视频视图销毁（离屏）时回调。
 */
@Composable
fun VideoSurfaceHost(
    modifier: Modifier = Modifier,
    onSurfaceReady: (TextureView) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        onSurfaceReady(this@apply)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        // 视频尺寸变化无需额外处理，内核按 surfaceTexture 渲染。
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        onSurfaceDestroyed()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        // 每帧更新无需处理。
                    }
                }
            }
        },
    )
}
