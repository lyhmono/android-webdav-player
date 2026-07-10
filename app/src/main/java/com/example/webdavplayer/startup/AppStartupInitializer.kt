package com.example.webdavplayer.startup

import android.content.Context
import androidx.startup.Initializer

/**
 * App Startup 初始化器（§1.5 / T11 冷启动优化）。
 *
 * 仅做轻量初始化，不在此进行网络请求或重活，满足
 * 「Application.onCreate 不做重活、进应用再懒连接」的约定。
 * profileinstaller 会自动安装 Baseline Profile，无需在此显式处理。
 */
class AppStartupInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // 冷启动轻量钩子：预留（如 StrictMode 配置、Baseline Profile 预热）。
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
