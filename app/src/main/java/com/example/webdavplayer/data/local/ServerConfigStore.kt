package com.example.webdavplayer.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.webdavplayer.domain.model.AuthType
import com.example.webdavplayer.domain.model.ServerConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务器配置持久化（§1.5 / T11）。
 *
 * 采用 Security-Crypto 的 [EncryptedSharedPreferences]：凭据（含密码）在落库时由
 * Android 密钥库加密，满足「冷启动加密存储 + 进应用再懒连接」的约定。
 */
@Singleton
class ServerConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 延迟初始化加密偏好。
     *
     * 关键修复（冷启动闪退）：原先在构造器里同步执行
     * [EncryptedSharedPreferences.create] + [MasterKey] 创建，而本类是 [@Singleton]，
     * 会在首屏 [androidx.hilt.navigation.compose.hiltViewModel] 构造
     * ServerListViewModel 时被主线程同步拉起（setContent 组合阶段）。一旦 Keystore /
     * 磁盘 I/O 抛异常，异常沿 hiltViewModel -> setContent -> MainActivity.onCreate
     * 上抛，表现为点图标瞬间闪退。改为 [lazy] 后，单例构造本身不再触碰 Keystore，
     * 重活推迟到首次实际读写时，且不会阻塞 Hilt 图构建与冷启动。
     */
    private val prefs: SharedPreferences by lazy { createPrefs() }

    /**
     * 创建加密偏好；任何异常（设备密钥库不支持 / Tink 缺失 / 旧 ROM 异常等）一律降级为
     * 明文 [SharedPreferences]，保证应用可正常启动，不阻塞冷启动。
     */
    private fun createPrefs(): SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "webdav_servers",
            MasterKey.Builder(context).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        context.getSharedPreferences("webdav_servers", Context.MODE_PRIVATE)
    }

    /** 观察所有服务器（变更即发新值）。 */
    fun observe(): Flow<List<ServerConfig>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(loadAll())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(loadAll())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun loadAll(): List<ServerConfig> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { parse(json) }.getOrDefault(emptyList())
    }

    fun saveAll(list: List<ServerConfig>) {
        prefs.edit { putString(KEY, serialize(list)) }
    }

    private companion object {
        const val KEY = "servers_json"

        fun serialize(list: List<ServerConfig>): String = JSONArray().apply {
            list.forEach { s ->
                put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("baseUrl", s.baseUrl)
                        put("username", s.username)
                        put("encryptedPassword", s.encryptedPassword)
                        put("authType", s.authType.name)
                        put("trustSelfSigned", s.trustSelfSigned)
                        put("createdAt", s.createdAt)
                    },
                )
            }
        }.toString()

        fun parse(json: String): List<ServerConfig> {
            val arr = JSONArray(json)
            val out = mutableListOf<ServerConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += ServerConfig(
                    id = o.getString("id"),
                    name = o.optString("name", ""),
                    baseUrl = o.getString("baseUrl"),
                    username = o.optString("username", ""),
                    encryptedPassword = o.optString("encryptedPassword", ""),
                    authType = runCatching { AuthType.valueOf(o.optString("authType", "NONE")) }
                        .getOrDefault(AuthType.NONE),
                    trustSelfSigned = o.optBoolean("trustSelfSigned", false),
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
            return out
        }
    }
}
