package com.example.webdavplayer.data.remote

import com.example.webdavplayer.domain.exception.CertUntrustedException
import com.example.webdavplayer.domain.model.AuthType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.repository.TrustedCertRepository
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.thegrizzlylabs.sardineandroid.DavResource
import com.example.webdavplayer.domain.model.MediaTypeClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Buffer
import okio.Source
import okio.source
import java.net.URLConnection
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 Sardine-Android 的 WebDAV 实现（§3 data/remote）。
 *
 * - 每个 [connect] 在 [baseClient] 基础上构建专属 OkHttpClient（自签信任 + Basic/Digest 鉴权）并缓存；
 * - PROPFIND Depth:1 单层列举 → 映射为 [RemoteFile]；
 * - 自签证书未信任时，[connect] 抛出 [CertUntrustedException]（携带指纹/颁发者）。
 */
@Singleton
class SardineWebDavClient @Inject constructor(
    private val baseClient: OkHttpClient,
    private val trustedCertRepository: TrustedCertRepository,
) : WebDavClient {

    @Volatile private var client: OkHttpClient? = null
    @Volatile private var activeConfig: ServerConfig? = null

    private fun requireClient(): OkHttpClient =
        client ?: throw IllegalStateException("尚未连接：请先调用 connect(config)")

    private fun requireConfig(): ServerConfig =
        activeConfig ?: throw IllegalStateException("尚未连接：请先调用 connect(config)")

    override suspend fun connect(config: ServerConfig) = withContext(Dispatchers.IO) {
        val fingerprints = trustedCertRepository.getFingerprints(config.id).toSet()
        val trustManager = SelfSignedTrustManager(
            delegate = SelfSignedTrustManager.systemDefault(),
            trustedFingerprints = fingerprints,
            allowSelfSigned = config.trustSelfSigned,
        )

        val builder = baseClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        when (config.authType) {
            AuthType.BASIC -> builder.addInterceptor(
                BasicAuthInterceptor(config.username, config.encryptedPassword),
            )
            AuthType.DIGEST -> builder.authenticator(
                WebDavAuthenticator(config.username, config.encryptedPassword, AuthType.DIGEST),
            )
            AuthType.NONE -> { /* 无鉴权 */ }
        }

        val sslContext = SelfSignedTrustManager.buildSslContext(trustManager)
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        if (config.trustSelfSigned) {
            builder.hostnameVerifier { _, _ -> true }
        }

        val built = builder.build()

        // 触发一次握手以校验信任（失败即捕获指纹供确认弹窗）
        val probe = OkHttpSardine(built)
        try {
            probe.list(config.baseUrl, 0)
        } catch (e: Exception) {
            val info = trustManager.lastSeenCert
            if (info != null) {
                throw CertUntrustedException(info.sha256, info.issuer)
            }
            throw e
        }

        client = built
        activeConfig = config
    }

    override suspend fun listDirectory(path: String, depth: Int): List<RemoteFile> =
        withContext(Dispatchers.IO) {
            val cfg = requireConfig()
            val sardine = OkHttpSardine(requireClient())
            val url = WebDavPath.join(cfg.baseUrl, path)
            val resources = retryIO { sardine.list(url, depth) }
            val parent = WebDavPath.normalize(path)
            resources.asSequence()
                .drop(1) // 首个元素是目录自身
                .map { mapResource(it, cfg.id, parent) }
                .toList()
        }

    override suspend fun openStream(path: String): Source = withContext(Dispatchers.IO) {
        val cfg = requireConfig()
        val sardine = OkHttpSardine(requireClient())
        val input = retryIO { sardine.get(WebDavPath.join(cfg.baseUrl, path)) }
        input.source()
    }

    override suspend fun upload(path: String, source: Source, size: Long?) =
        withContext(Dispatchers.IO) {
            val cfg = requireConfig()
            val sardine = OkHttpSardine(requireClient())
            val url = WebDavPath.join(cfg.baseUrl, path)
            val contentType = guessContentType(path)
            val bytes = Buffer().apply { writeAll(source) }.readByteArray()
            sardine.put(url, bytes, contentType)
        }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        val cfg = requireConfig()
        val parent = WebDavPath.parentOf(from)
        val targetName = WebDavPath.nameOf(to) // to 为目标名称
        val toPath = if (parent == "/") "/$targetName" else "$parent/$targetName"
        val sardine = OkHttpSardine(requireClient())
        retryIO {
            sardine.move(WebDavPath.join(cfg.baseUrl, from), WebDavPath.join(cfg.baseUrl, toPath))
        }
    }

    override suspend fun move(from: String, to: String) = withContext(Dispatchers.IO) {
        val cfg = requireConfig()
        // [to] 为目标目录相对路径，文件名取自 [from]
        val name = WebDavPath.nameOf(from)
        val dest = if (to == "/") "/$name" else "$to/$name"
        val sardine = OkHttpSardine(requireClient())
        retryIO {
            sardine.move(WebDavPath.join(cfg.baseUrl, from), WebDavPath.join(cfg.baseUrl, dest))
        }
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val cfg = requireConfig()
        val sardine = OkHttpSardine(requireClient())
        retryIO { sardine.delete(WebDavPath.join(cfg.baseUrl, path)) }
    }

    override fun getOkHttpClient(): OkHttpClient = requireClient()

    private fun mapResource(res: DavResource, serverId: String, parentPath: String): RemoteFile {
        val href = res.href?.toString() ?: ""
        val name = href.substringAfterLast('/').ifEmpty { href }
        val contentType = res.contentType ?: ""
        return RemoteFile(
            id = "$serverId:$parentPath/$name",
            serverId = serverId,
            parentPath = parentPath,
            name = name,
            isDirectory = res.isDirectory,
            size = res.contentLength,
            contentType = contentType,
            lastModified = res.modified?.time ?: 0L,
            eTag = res.etag ?: "",
            mediaType = MediaTypeClassifier.classify(contentType, name),
        )
    }

    private fun guessContentType(path: String): String =
        URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream"

    private fun <T> retryIO(
        maxRetries: Int = 3,
        initialDelayMs: Long = 500L,
        block: () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: java.io.IOException) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    val delay = initialDelayMs * (1 shl attempt) // 500, 1000, 2000 ms
                    Thread.sleep(delay)
                }
            }
        }
        throw lastError ?: java.io.IOException("retryIO: unknown failure")
    }
}
