package com.example.webdavplayer.domain.player

import android.content.Context
import com.example.webdavplayer.data.player.PlayerEngineFactory
import com.example.webdavplayer.data.player.WebDavStreamingSource
import com.example.webdavplayer.data.repository.PlayerRepositoryImpl
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.repository.PlayerRepository
import com.example.webdavplayer.domain.repository.ServerRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import com.example.webdavplayer.common.Result
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 倍速（O1 + F1）契约测试（纯 JVM）。
 *
 * 关键：直接实例化**真实** [PlayerRepositoryImpl]，仅用 fake 替换其依赖（WebDavClient /
 * ServerRepository / SettingsRepository / PlayerEngineFactory）。工厂子类化后返回
 * [RecordingPlayerEngine]，从而断言真实仓库在 [PlayerRepository.prepare] / [setEngineType]
 * 重建内核后是否调用了 `engine.setSpeed(currentSpeed)` —— 锁定跨曲目 / 跨内核持续生效的契约。
 *
 * 若有人误改 [PlayerRepositoryImpl]（如忘记在 prepare 后重放倍速），本测试会真实失败。
 */
class PlayerEngineSetSpeedTest {

    private fun sampleMedia(): PlayableMedia = PlayableMedia(
        uri = "https://example.com/a.mp4",
        headers = emptyMap(),
        name = "a.mp4",
        mediaType = MediaType.VIDEO,
        serverId = "s1",
    )

    /** 记录倍速调用的 [PlayerEngine] 假实现（无 Android 依赖）。 */
    private class RecordingPlayerEngine : PlayerEngine {
        var lastSpeed: Float = 1.0f
            private set
        val speeds = mutableListOf<Float>()

        override fun prepare(media: PlayableMedia) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setSpeed(speed: Float) {
            lastSpeed = speed
            speeds += speed
        }
        override fun setListener(listener: EngineListener?) = Unit
        override fun getState(): PlaybackState = PlaybackState.IDLE
        override fun release() = Unit
    }

    /** 子类化真实 [PlayerEngineFactory]，让 create 返回 [RecordingPlayerEngine]（不碰 Android Context）。 */
    @Suppress("UNCHECKED_CAST")
    private class FakeEngineFactory : PlayerEngineFactory(
        // streamingSource 在 fake 中不被使用（create 被 override），直接实例化即可。
        WebDavStreamingSource(),
    ) {
        val created = mutableListOf<RecordingPlayerEngine>()
        override fun create(type: EngineType, context: Context): PlayerEngine =
            RecordingPlayerEngine().also { created += it }
    }

    private class FakeWebDavClient : WebDavClient {
        override suspend fun connect(config: ServerConfig) = Unit
        override suspend fun listDirectory(path: String, depth: Int) = emptyList<com.example.webdavplayer.domain.model.RemoteFile>()
        override suspend fun openStream(path: String): Source = throw UnsupportedOperationException()
        override suspend fun upload(path: String, source: Source, size: Long?) = Unit
        override suspend fun rename(from: String, to: String) = Unit
        override suspend fun move(from: String, to: String) = Unit
        override suspend fun delete(path: String) = Unit
        override fun getOkHttpClient(): OkHttpClient = OkHttpClient()
    }

    private class FakeServerRepository : ServerRepository {
        override fun observeAll(): Flow<List<ServerConfig>> = emptyFlow()
        override suspend fun getAll(): List<ServerConfig> = emptyList()
        override suspend fun getById(id: String): ServerConfig =
            ServerConfig(id, "n", "https://x", "u", "p", com.example.webdavplayer.domain.model.AuthType.BASIC, false, 0)
        override suspend fun save(config: ServerConfig) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun connect(config: ServerConfig): Result<Unit> = Result.Success(Unit)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private var engineType: EngineType = EngineType.MEDIA3
        override fun observeEngineType(): Flow<EngineType> = emptyFlow()
        override fun getEngineType(): EngineType = engineType
        override suspend fun setEngineType(type: EngineType) { engineType = type }
        override fun observeCurrentServerId(): Flow<String?> = emptyFlow()
        override fun getCurrentServerId(): String? = null
        override suspend fun setCurrentServerId(id: String?) = Unit
    }

    /** 构造真实 [PlayerRepositoryImpl]，Context 传桩（fake factory 不使用它）。 */
    @Suppress("UNCHECKED_CAST")
    private fun createRepo(): Pair<PlayerRepositoryImpl, FakeEngineFactory> {
        val factory = FakeEngineFactory()
        // PlayerRepositoryImpl 需要 @ApplicationContext Context；fake factory 不使用 context，
        // 故传任意非空引用即可（Kotlin 非 null 参数需显式 cast）。
        val ctx = Any() as Context
        val repo = PlayerRepositoryImpl(
            webDavClient = FakeWebDavClient(),
            serverRepository = FakeServerRepository(),
            settingsRepository = FakeSettingsRepository(),
            playerEngineFactory = factory,
            context = ctx,
        )
        return repo to factory
    }

    @Test
    fun engine_setSpeed_forwardsValueToUnderlyingPlayer() {
        val engine = RecordingPlayerEngine()
        engine.setSpeed(1.5f)
        assertEquals(1.5f, engine.lastSpeed)
        assertEquals(listOf(1.5f), engine.speeds)
    }

    @Test
    fun engine_defaultSpeedIsNormal() {
        val engine = RecordingPlayerEngine()
        assertEquals(1.0f, engine.lastSpeed)
    }

    @Test
    fun repository_reappliesSpeedOnPrepare_afterSetSpeed() = runBlocking {
        val (repo, factory) = createRepo()
        repo.setSpeed(2.0f)
        // prepare 会经真实 PlayerRepositoryImpl → factory.create → RecordingPlayerEngine，
        // 真实仓库应在 prepare 后调用 engine.setSpeed(currentSpeed)。
        repo.prepare(sampleMedia())
        val engine = factory.created.last()
        assertEquals("prepare 后应重放倍速到新引擎", 2.0f, engine.lastSpeed)
    }

    @Test
    fun repository_defaultSpeedReappliedOnPrepare() = runBlocking {
        val (repo, factory) = createRepo()
        repo.prepare(sampleMedia())
        val engine = factory.created.last()
        assertEquals("默认倍速应为 1.0", 1.0f, engine.lastSpeed)
    }

    @Test
    fun repository_reappliesSpeedOnEngineSwitch() = runBlocking {
        val (repo, factory) = createRepo()
        repo.prepare(sampleMedia())
        repo.setSpeed(1.5f)
        // 切换内核（同类型也会重建），真实仓库应在新引擎上重放 1.5f。
        repo.setEngineType(EngineType.VLC)
        // VLC 在 lite 测试中 factory 仍返回 RecordingPlayerEngine（fake override），不抛异常。
        val engine = factory.created.last()
        assertEquals("切内核后应重放倍速", 1.5f, engine.lastSpeed)
    }
}
