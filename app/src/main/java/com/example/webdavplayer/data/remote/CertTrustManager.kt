package com.example.webdavplayer.data.remote

import com.example.webdavplayer.domain.exception.CertUntrustedException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 自签证书信任管理器（§1.4 / §8）。
 *
 * - 正常 CA 签发的证书：委托系统默认 [X509TrustManager]。
 * - 若系统不信任（自签/私有 CA）且 `allowSelfSigned=true`：
 *   检查证书链中是否有 SHA-256 指纹命中 [trustedFingerprints]（永久信任库）；
 *   命中则放行，否则抛出 [CertUntrustedException]（携带指纹+颁发者供确认弹窗）。
 * - `lastSeenCert` 始终记录最近一次握手呈现的证书信息，供首次连接失败后的确认弹窗使用。
 */
class SelfSignedTrustManager(
    private val delegate: X509TrustManager,
    private val trustedFingerprints: Set<String>,
    private val allowSelfSigned: Boolean,
) : X509TrustManager {

    data class CertInfo(
        val sha256: String,
        val issuer: String,
        val subject: String,
    )

    /** 最近一次握手呈现的证书信息（供确认弹窗）。 */
    @Volatile
    var lastSeenCert: CertInfo? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull()
        if (leaf != null) {
            lastSeenCert = CertInfo(
                sha256 = sha256(leaf),
                issuer = leaf.issuerX500Principal.name,
                subject = leaf.subjectX500Principal.name,
            )
        }

        if (allowSelfSigned) {
            try {
                delegate.checkServerTrusted(chain, authType) // 正常 CA 直接通过
                return
            } catch (_: Exception) {
                // 系统不信任 → 看是否命中用户信任指纹集
                val matched = chain.any { sha256(it) in trustedFingerprints }
                if (matched) return
                val info = lastSeenCert
                throw CertUntrustedException(
                    sha256Fingerprint = info?.sha256 ?: "",
                    issuer = info?.issuer ?: "",
                )
            }
        } else {
            delegate.checkServerTrusted(chain, authType)
        }
    }

    private fun sha256(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** 获取系统默认 X509 信任管理器。 */
        fun systemDefault(): X509TrustManager {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }

        /** 基于信任管理器构建 SSLContext。 */
        fun buildSslContext(
            trustManager: X509TrustManager,
        ): SSLContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
    }
}
