package com.example.webdavplayer.domain.model

/**
 * 服务器配置（领域模型，纯 Kotlin）。
 *
 * 凭据通过 Security-Crypto(EncryptedSharedPreferences) 加密存储（§1.5 / T11），
 * 因此 [encryptedPassword] 在内存中为明文凭据，落库时由 EncryptedSharedPreferences 加密。
 */
data class ServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    /** 明文凭据（落库由 EncryptedSharedPreferences 加密）。 */
    val encryptedPassword: String,
    val authType: AuthType,
    val trustSelfSigned: Boolean,
    val createdAt: Long,
)
