package com.example.webdavplayer.data.player

import android.content.Context
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.player.PlayerEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放内核工厂（§4.2 / §1.2）。
 *
 * 按当前 [EngineType] 产出实现；应用内切换内核 = `release()` 旧 + `create()` 新 + `prepare()` 当前媒体。
 *
 * VLC 引擎（[VlcEngine]）仅在 `full` 风味中编译（见 src/full），
 * 此处通过反射加载，使 `lite` 风味（仅 Media3）也能编译通过。
 *
 * 标记为 `open` 仅供纯 JVM 单测子类化替换内核产出（验证 [com.example.webdavplayer.data.repository.PlayerRepositoryImpl]
 * 的倍速重放契约），生产代码不应子类化。
 */
@Singleton
open class PlayerEngineFactory @Inject constructor(
    private val streamingSource: WebDavStreamingSource,
) {
    open fun create(type: EngineType, context: Context): PlayerEngine = when (type) {
        EngineType.MEDIA3 -> ExoPlayerEngine(context, streamingSource)
        EngineType.VLC -> createVlc(context)
    }

    private fun createVlc(context: Context): PlayerEngine = try {
        val clazz = Class.forName("com.example.webdavplayer.data.player.VlcEngine")
        clazz.getConstructor(Context::class.java).newInstance(context) as PlayerEngine
    } catch (e: Throwable) {
        throw IllegalStateException(
            "VLC 内核不可用：当前构建为 lite 风味（仅 Media3）。请使用 full 风味。",
            e,
        )
    }
}
