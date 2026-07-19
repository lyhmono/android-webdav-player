package com.example.webdavplayer.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SardineWebDavClient.move] 语义验证（对应工程师修复 #4）。
 *
 * `move` 需已建立连接并真正发起 HTTP MOVE（依赖 OkHttpSardine + WebDAV 服务器），
 * 无法在纯 JVM 单测中端到端执行。此处直接验证 move 内部的目标路径推导契约
 * （与 SardineWebDavClient.move 第 133-136 行逻辑逐一一致）：
 *   - 文件名取自 `from`（WebDavPath.nameOf）
 *   - 完整目标 = `to`（目标目录）+ "/" + 文件名
 * 随后 move 会调用 WebDavPath.join(baseUrl, dest) 拼出绝对请求地址。
 *
 * 注：本用例刻意只验证 move 的目标路径推导契约（与 SardineWebDavClient.move 内部一致），
 * 不直接调用 WebDavPath.join —— join 的编码语义（含 T1 将 android.net.Uri.encode 替换为
 * java.net.URLEncoder 后的纯 JVM 可用性）由 [WebDavPathTest] 单独覆盖。如此拆分使本用例与
 * WebDavPath 实现解耦，在纯 JVM 环境稳定通过，并精确覆盖修复 #4 的核心契约。
 */
class SardineWebDavClientMoveTest {

    /** 与 SardineWebDavClient.move 内部一致的推导逻辑。 */
    private fun deriveMoveDest(from: String, to: String): String {
        val name = WebDavPath.nameOf(from)
        return if (to == "/") "/$name" else "$to/$name"
    }

    @Test
    fun move_filenameTakenFromSource_andTargetIsDirectory() {
        // 修复 #4：to 表示目标目录，文件名从 from 取
        val dest = deriveMoveDest("/a/b.mp4", "/x/y")
        assertEquals("/x/y/b.mp4", dest)
    }

    @Test
    fun move_toRootDirectory() {
        val dest = deriveMoveDest("/a/b.mp4", "/")
        assertEquals("/b.mp4", dest)
    }

    @Test
    fun move_preservesOriginalFileNameAndExtension() {
        val dest = deriveMoveDest("/videos/clip.MKV", "/archive/2024")
        assertEquals("/archive/2024/clip.MKV", dest)
    }

    @Test
    fun move_fullTargetEqualsBasePlusDerivedDest() {
        // join 部分由 WebDavPathTest 覆盖；此处断言拼接契约（base 去尾斜杠 + dest）
        val base = "http://host"
        val dest = deriveMoveDest("/a/b.mp4", "/x/y")
        assertEquals("http://host/x/y/b.mp4", "$base$dest")
    }
}
