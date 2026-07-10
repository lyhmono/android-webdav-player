package com.example.webdavplayer.domain.exception

/** 鉴权失败（401/403 或凭据错误）。 */
class AuthFailException(message: String = "authentication failed") : Exception(message)

/** 服务器证书未被信任（自签证书首连确认流程触发）。 */
class CertUntrustedException(
    val sha256Fingerprint: String,
    val issuer: String,
) : Exception("certificate not trusted: $issuer ($sha256Fingerprint)")

/** WebDAV 资源未找到（404）。 */
class WebDavNotFoundException(message: String = "resource not found") : Exception(message)
