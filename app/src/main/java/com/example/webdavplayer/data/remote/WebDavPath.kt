package com.example.webdavplayer.data.remote

import java.net.URLEncoder

/**
 * WebDAV 路径规范化（§8 共享约定）。
 * - 统一去尾斜杠、合并多斜杠、反斜杠转正斜杠；
 * - 拼接时对每个路径段做 URL 编码（中文/空格）；
 * - `baseUrl + 规范化相对路径` 即绝对请求地址。
 *
 * C6（P1）：拼接编码由 `android.net.Uri.encode` 替换为纯 JVM 实现，去除 Android 依赖，
 * 使 [join] 在普通 JVM 单测（无 Robolectric）下可用，语义与 P0 §8 约定保持一致。
 */
object WebDavPath {
    /** RFC 3986 非保留字符：放行，不被编码（URLEncoder 已放行 A-Za-z0-9-_.，本实现再还原 `~`）。 */
    private val UNRESERVED = setOf('-', '_', '.', '~')

    /** 规范化：去尾 '/'(根目录除外)、合并重复 '/'. */
    fun normalize(path: String): String {
        val p = path.trim().replace('\\', '/').replace(Regex("/+"), "/")
        return if (p == "/") "/" else p.trimEnd('/')
    }

    /** 将 base 与相对路径拼接为绝对 URL（自动编码各段）。 */
    fun join(base: String, relative: String): String {
        val b = base.trimEnd('/')
        val segments = normalize(relative)
            .split('/')
            .filter { it.isNotEmpty() }
            .map { encodeSegment(it) }
        return if (segments.isEmpty()) "$b/" else (listOf(b) + segments).joinToString("/")
    }

    /** 取父目录（规范化的路径）。 */
    fun parentOf(path: String): String {
        val p = normalize(path)
        if (p == "/") return "/"
        val idx = p.lastIndexOf('/')
        return if (idx <= 0) "/" else p.substring(0, idx)
    }

    /** 取末段名称。 */
    fun nameOf(path: String): String = normalize(path).substringAfterLast('/')

    /**
     * 段编码：先经 [URLEncoder]（UTF-8），空格转 `+` 后统一为 `%20`；
     * 再把 RFC 3986 非保留字符（`-_.~`）还原，使语义与 P0 `Uri.encode` 一致且不依赖 Android。
     */
    private fun encodeSegment(segment: String): String {
        val encoded = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        return buildString(encoded.length) {
            var i = 0
            while (i < encoded.length) {
                if (encoded[i] == '%' && i + 2 < encoded.length) {
                    val hex = encoded.substring(i + 1, i + 3)
                    val ch = hex.toIntOrNull(16)?.toChar()
                    if (ch != null && ch in UNRESERVED) {
                        append(ch)
                        i += 3
                        continue
                    }
                }
                append(encoded[i])
                i++
            }
        }
    }
}
