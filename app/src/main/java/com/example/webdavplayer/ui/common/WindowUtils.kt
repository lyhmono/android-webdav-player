package com.example.webdavplayer.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * 从 Context 向上查找 [ComponentActivity]（用于获取 Window 调节亮度 / 沉浸模式等）。
 *
 * 原实现位于 `ui/player/VideoGestureLayer.kt`，为便于多处复用（手势层、沉浸模式）
 * 迁移到本公共工具文件。
 */
fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
