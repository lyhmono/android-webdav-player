package com.example.webdavplayer

import android.app.Application
import com.example.webdavplayer.data.network.NetworkMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** 应用入口（Hilt）。onCreate 不做重活（§1.5 冷启动优化）。 */
@HiltAndroidApp
class WebDavPlayerApp : Application() {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()
        // 启动网络状态监控（全局单例，UI 和 Service 均可消费）
        networkMonitor.startMonitoring()
    }

    override fun onTerminate() {
        networkMonitor.stopMonitoring()
        super.onTerminate()
    }
}
