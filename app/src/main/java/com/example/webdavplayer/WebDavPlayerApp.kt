package com.example.webdavplayer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** 应用入口（Hilt）。onCreate 不做重活（§1.5 冷启动优化）。 */
@HiltAndroidApp
class WebDavPlayerApp : Application()
