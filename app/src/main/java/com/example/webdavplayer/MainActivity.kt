package com.example.webdavplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.webdavplayer.service.PlaybackService
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
        // 启动后台媒体会话服务（C1）：承载 MediaSession，供 MediaController 连接。
        // Media3 的 MediaSessionService 会在媒体播放时自动以前台服务 + 通知形式运行。
        // Android 12+ 不允许在后台无条件启动前台服务，这里使用 startService
        // 而非 startForegroundService，服务在首次播放时自动晋升为前台服务。
        val intent = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        setContent { AppRoot() }
    }
}
