package com.example.webdavplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.example.webdavplayer.service.PlaybackService
import com.example.webdavplayer.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

/** 主活动：承载 Compose 与导航根。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动后台媒体会话服务（C1）：承载 MediaSession，供 MediaController 连接。
        // Media3 的 MediaSessionService 会在媒体播放时自动以前台服务 + 通知形式运行。
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        setContent { AppRoot() }
    }
}
