package com.example.webdavplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.webdavplayer.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

/** 主活动：承载 Compose 与导航根。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 (SDK 35)：targetSdk 35 后系统默认 edge-to-edge，
        // 显式调用以确保内容延伸到系统栏后方，配合 Compose 窗口内边距使用。
        enableEdgeToEdge()
        // PlaybackService 由 Media3 自动管理生命周期：
        // - PlayerViewModel 中的 MediaController.Builder 连接时会自动 bindService；
        // - 首次播放时 MediaSessionService 自动晋升为前台服务 + 通知；
        // - 不在 onCreate 中手动 startForegroundService，避免无媒体播放时
        //   触发 ForegroundServiceDidNotStartInTimeException（Android 12+）。
        setContent { AppRoot() }
    }
}
