package com.example.webdavplayer.data.remote

import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.model.ServerConfig
import okhttp3.OkHttpClient
import okio.Source

/**
 * WebDAV 客户端抽象（§4.2 / §3 data/remote）。
 * 屏蔽 Sardine / 自签 SSL / 鉴权细节；上层（仓库）只依赖此接口与领域模型。
 *
 * 约定：方法均应在 IO 线程调用（§8，禁止主线程 PROPFIND/上传）。
 */
interface WebDavClient {
    /** 建立连接：构建并缓存该服务器专属 OkHttpClient（含自签信任 + 鉴权）。触发 TLS 握手以校验信任。 */
    suspend fun connect(config: ServerConfig)

    /** 列举目录（Depth 默认 1，单层；§1.3）。 */
    suspend fun listDirectory(path: String, depth: Int = 1): List<RemoteFile>

    /** 打开流式输入（供内核边下边播；ExoPlayer 实际复用下方 OkHttp 作 DataSource）。 */
    suspend fun openStream(path: String): Source

    /** 单文件上传（P0）。 */
    suspend fun upload(path: String, source: Source, size: Long?)

    /** 重命名（同目录内改名，[to] 为目标名称）。 */
    suspend fun rename(from: String, to: String)

    /** 移动（可跨目录，[from] 为完整相对路径，[to] 为目标目录相对路径，文件名取自 [from]）。 */
    suspend fun move(from: String, to: String)

    /** 删除文件或目录。 */
    suspend fun delete(path: String)

    /** 取当前连接缓存的 OkHttpClient（供流式播放复用自签信任 + 鉴权）。 */
    fun getOkHttpClient(): OkHttpClient
}
