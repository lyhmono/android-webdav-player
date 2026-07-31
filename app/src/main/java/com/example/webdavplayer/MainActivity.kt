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
        setContent { AppRoot() }
    }
}
