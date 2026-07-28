package com.example.webdavplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.webdavplayer.ui.common.findActivity

/**
 * 全屏沉浸模式副作用（仅全屏态启用）。
 *
 * 通过 [WindowInsetsControllerCompat] 在 [enabled] 时隐藏状态栏与导航栏，
 * 并设为「滑动即短暂显示」；退出或 onDispose 时恢复系统栏。
 *
 * @param enabled true=进入沉浸（隐藏系统栏），false=恢复系统栏。
 */
@Composable
fun ImmersiveModeEffect(enabled: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity()
    DisposableEffect(enabled) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (enabled) {
                controller.hide(
                    WindowInsetsCompat.Type.statusBars()
                        or WindowInsetsCompat.Type.navigationBars(),
                )
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
