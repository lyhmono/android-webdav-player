# VLC 认证 + 全屏修复 + UI 重写（commit `d14e4c8`，CI 通过）

**日期**：2026-07-24  
**Commit**：`d14e4c8`  
**CI**：✅ Lite + Full 双 APK 构建成功（run 30023644037，2m33s）

## 修复内容

### 1. VLC HTTP 认证修复（VlcEngine.kt）

**根因**：之前用 `:http-header=Authorization: Basic xxx` 格式传认证头给 VLC media，但 VLC 不认识这个 option。

**修复**：改用 VLC 原生的 HTTP 认证选项：
- 从 `Authorization: Basic xxx` header 中 Base64 解码出 `user:pwd`
- 使用 `:http-user=$user` 和 `:http-pwd=$pwd` 两个 media option

```kotlin
val authHeader = media.headers["Authorization"]
if (authHeader != null && authHeader.startsWith("Basic ")) {
    val decoded = String(android.util.Base64.decode(authHeader.substring(6), android.util.Base64.DEFAULT))
    val colonIdx = decoded.indexOf(':')
    if (colonIdx > 0) {
        val user = decoded.substring(0, colonIdx)
        val pwd = decoded.substring(colonIdx + 1)
        m.addOption(":http-user=$user")
        m.addOption(":http-pwd=$pwd")
    }
}
```

### 2. VLC Surface 绑定时序修复（VlcEngine.kt）

**根因**：`TextureView` 的 `onSurfaceTextureAvailable` 回调在 `mediaPlayer` 创建之前触发，导致 `vlcVout` 绑定失败。

**修复**：
- 新增 `currentSurface: Surface?` 字段持有 Surface 引用
- `attachVlcVout()` 幂等方法：在三个时机均尝试绑定
  1. `onSurfaceTextureAvailable` 回调
  2. `prepare()` 中
  3. `MediaPlayer.Event.Playing` 事件
- Playing 事件触发时再次 attach（surface 可能此刻才 ready）

### 3. 全屏模式修复（PlayerScreen.kt）

**根因**：之前用 `return` 语句提前返回，导致 Compose 重组时 `DisposableEffect` 的 `onDispose` 被触发，立刻退出全屏。

**修复**：
- 改用 `if/else` 分支，不 `return` 出 Composable 函数
- `DisposableEffect(isFullScreen)` 正确管理系统 UI 可见性
- 全屏时：横屏 + `SYSTEM_UI_FLAG_FULLSCREEN | IMMERSIVE_STICKY`
- 退出时恢复原始方向和系统 UI

### 4. 播放界面 UI 重写（PlayerScreen.kt）

**非全屏模式**：
- 视频用 `PlayerView`（Media3）或 `TextureView`（VLC），16:9 宽高比
- 视频模式：用 PlayerView 自带控制器，不再加冗余的 Compose 按钮
- 音频模式：Compose 按钮（上一首/播放暂停/下一首）+ Slider 进度条
- 播放列表 + 播放模式 + 内核切换

**全屏模式**（FullScreenVideoPlayer）：
- `RESIZE_MODE_FILL`（视频拉伸填满全屏）
- 点击画面切换控制层（淡入淡出）
- 左右滑动手势 seek：拖拽时显示目标时间气泡
- 底部控制栏：Slider + 快退15s / 播放暂停 / 快进15s + 时间显示
- 顶部：退出全屏按钮 + 标题

### 5. 退出播放界面停止播放

```kotlin
DisposableEffect(Unit) {
    onDispose { playerVm.stop() }
}
```

`stop()` 内部：`mediaController?.stop()` + `playerRepository.release()` + 清空 StateFlow

## 修改文件

| 文件 | 改动 |
|------|------|
| `app/src/full/java/.../VlcEngine.kt` | HTTP 认证 + Surface 时序修复 |
| `app/src/main/java/.../PlayerScreen.kt` | 全屏修复 + UI 重写 |

## 待测试

- [ ] VLC 是否能播放 WebDAV 视频（认证 + Surface 双修复后）
- [ ] 全屏模式是否稳定（不再立刻退出）
- [ ] 退出播放界面是否停止播放
- [ ] 滑动手势 seek 是否正常工作

## APK 产物

- Lite APK：`downloaded-apks/app-lite-debug.apk`（21MB）
- Full APK：CI 构建成功，下载中
