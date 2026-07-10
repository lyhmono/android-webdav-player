package com.example.webdavplayer.data.local

import android.content.Context
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
    private val prefs = EncryptedSharedPreferences.create(
        "webdav_servers",
        MasterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

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
