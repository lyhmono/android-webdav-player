package com.example.webdavplayer.ui.player

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 承载视频渲染的 [SurfaceView]（穿透抽象层直达内核）。
 *
 * 通过 [android.view.SurfaceHolder.Callback] 把 Surface 生命周期回调给上层：
 * - [SurfaceHolder.Callback.surfaceCreated] → [onSurfaceReady]（绑定到引擎）；
 * - [SurfaceHolder.Callback.surfaceDestroyed] → [onSurfaceDestroyed]（解绑）。
 *
 * @param modifier 外层布局修饰（在 PlayerScreen 中传入视频区 Modifier）。
 * @param onSurfaceReady Surface 就绪（创建）时回调，参数为可用的 [Surface]。
 * @param onSurfaceDestroyed Surface 销毁（离屏）时回调。
 */
@Composable
fun VideoSurfaceHost(
    modifier: Modifier = Modifier,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceReady(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        // 视频尺寸变化无需额外处理，内核按 surface 渲染。
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceDestroyed()
                    }
                })
            }
        },
    )
}
