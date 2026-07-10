package com.example.webdavplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.webdavplayer.domain.model.TrustedCert

/** 已信任自签证书实体（§1.4）。 */
@Entity(tableName = "trusted_certs")
data class TrustedCertEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val sha256Fingerprint: String,
    val issuer: String,
    val trustedAt: Long,
)

fun TrustedCertEntity.toDomain(): TrustedCert = TrustedCert(
    id = id,
    serverId = serverId,
    sha256Fingerprint = sha256Fingerprint,
    issuer = issuer,
    trustedAt = trustedAt,
)

fun TrustedCert.toEntity(): TrustedCertEntity = TrustedCertEntity(
    id = id,
    serverId = serverId,
    sha256Fingerprint = sha256Fingerprint,
    issuer = issuer,
    trustedAt = trustedAt,
)
