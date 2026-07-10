package com.example.webdavplayer.data.remote

import com.example.webdavplayer.domain.model.AuthType
import kotlin.text.Charsets
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Route
import okhttp3.Authenticator
import java.security.MessageDigest
import java.util.UUID

/**
 * WebDAV 鉴权：Basic 预置拦截器 + Digest 质询应答（§6 / PRD ② Basic/Digest）。
 */
class BasicAuthInterceptor(
    private val username: String,
    private val password: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", Credentials.basic(username, password))
            .build()
        return chain.proceed(request)
    }
}

/**
 * Digest 鉴权应答器（OkHttp 原生不支持 digest，这里按 RFC 2617 计算）。
 * 仅在服务器返回 401 + `WWW-Authenticate: Digest ...` 时触发。
 */
class WebDavAuthenticator(
    private val username: String,
    private val password: String,
    private val authType: AuthType,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
        if (authType != AuthType.DIGEST) return null
        if (response.request.header("Authorization") != null) return null // 已尝试过，放弃

        val header = response.header("WWW-Authenticate") ?: return null
        if (!header.startsWith("Digest", ignoreCase = true)) return null

        val params = parseDigest(header)
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val opaque = params["opaque"]
        val qop = params["qop"] // 可能形如 "auth,auth-int"

        val method = response.request.method
        val uri = response.request.url.encodedPath
        val nc = "00000001"
        val cnonce = UUID.randomUUID().toString().take(8)

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")
        val responseDigest = if (qop != null) {
            val q = qop.split(",").map { it.trim() }.firstOrNull { it == "auth" } ?: qop
            md5("$ha1:$nonce:$nc:$cnonce:$q:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        val authHeader = buildString {
            append("Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$responseDigest\"")
            if (opaque != null) append(", opaque=\"$opaque\"")
            if (qop != null) append(", qop=auth, nc=$nc, cnonce=\"$cnonce\"")
        }
        return response.request.newBuilder().header("Authorization", authHeader).build()
    }

    private fun parseDigest(header: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val body = header.removePrefix("Digest").trim()
        val regex = Regex("""(\w+)=(?:"([^"]*)"|([^,]*))""")
        regex.findAll(body).forEach { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            map[key.lowercase()] = value.trim()
        }
        return map
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
