package com.example.webdavplayer.ui.player

import android.graphics.SurfaceTexture
import android.view.Gravity
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
 * 为确保视频不拉伸变形，LayoutParams 设为 MATCH_PARENT 宽度 +
 * WRAP_CONTENT 高度，让 TextureView 根据视频实际分辨率在容器内保持比例。
 *
 * @param modifier 外层布局修饰。
 * @param onSurfaceReady 视频视图就绪时回调，参数为可用的 [TextureView]。
 * @param onSurfaceDestroyed 视频视图销毁时回调。
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
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply { gravity = android.view.Gravity.CENTER }
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
                    ) { /* no-op */ }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        onSurfaceDestroyed()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { /* no-op */ }
                }
            }
        },
        update = { view ->
            // ensure layout updated
            view.requestLayout()
        },
    )
}
