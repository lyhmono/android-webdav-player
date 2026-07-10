package com.example.webdavplayer.domain.model

/**
 * 已信任的自签证书（§1.4 / §4.1）。
 * [sha256Fingerprint] 为信任主键，确认后永久写入（可于设置页移除）。
 */
data class TrustedCert(
    val id: String,
    val serverId: String,
    val sha256Fingerprint: String,
    val issuer: String,
    val trustedAt: Long,
)
