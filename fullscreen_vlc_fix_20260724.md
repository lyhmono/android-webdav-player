# 全屏退回 + VLC 黑屏根因修复

**日期**: 2026-07-24
**Commit**: `b48153b`
**CI**: ✅ 通过 (Lite + Full APK, 2m52s)

## 修复的根因

### 1. 全屏立刻退回（根因）

**旧代码**：`if (isFullScreen && isVideo) { FullScreenVideoPlayer(...) } else { Scaffold { ... DisposableEffect(Unit) { onDispose { playerVm.stop() } } ... } }`

**根因**：当 `isFullScreen` 从 false → true 时，`else` 分支的整个 `Scaffold` 被 Compose 移除，其内部 `DisposableEffect(Unit)` 的 `onDispose` 被触发 → `playerVm.stop()` → 播放停止 → state 变化 → recomposition → 可能导致状态混乱。

**修复方案**：全屏和非全屏使用**同一个 Scaffold**，全屏时仅隐藏 TopAppBar，内容区用 `if` 分支切换。`DisposableEffect(Unit)` 始终在外层，不会被全屏切换触发。

### 2. VLC 黑屏（根因）

**旧代码**：`mp.vlcVout.setVideoSurface(surface, null)` — 传 null holder

**根因**：VLC 的 `IVLCVout.setVideoSurface(Surface, SurfaceHolder)` 需要 holder 来管理 surface 生命周期。传 null 导致 VLC 无法正确绑定。此外，`Surface` 是从 `SurfaceHolder.Callback.surfaceCreated` 获取的，时序复杂且不稳定。

**修复方案**：改用 VLC 官方推荐方式 `setVideoView(SurfaceView) + attachViews()`：
- `VlcEngine.setSurface(Surface?)` → `VlcEngine.setSurfaceView(SurfaceView?)`
- `PlayerRepository.setVlcSurface(Surface?)` → `setVlcSurfaceView(SurfaceView?)`
- `PlayerViewModel.attachVlcSurface(Surface?)` → `attachVlcSurfaceView(SurfaceView?)`
- UI 层 `AndroidView` factory 直接传 `SurfaceView` 给 VlcEngine

## 修改文件

| 文件 | 变更 |
|------|------|
| `VlcEngine.kt` | `setSurface(Surface?)` → `setSurfaceView(SurfaceView?)`，`attachVlcVout()` 用 `setVideoView` + `attachViews()` |
| `PlayerRepository.kt` | 接口 `setVlcSurface(Surface?)` → `setVlcSurfaceView(SurfaceView?)` |
| `PlayerRepositoryImpl.kt` | 反射调用改为 `setSurfaceView` 方法 |
| `PlayerViewModel.kt` | `attachVlcSurface(Surface?)` → `attachVlcSurfaceView(SurfaceView?)` |
| `PlayerScreen.kt` | 全屏内容放在 Scaffold 内部，不再用 if/else 切换整个 Composable 树 |

## 参考来源

- VLC 官方示例 (`libvlc-android-samples`)：`vlcVout.setVideoView(mVideoSurface); vlcVout.attachViews();`
- JiaoZiVideoPlayer 全屏实现：Activity 级全屏（横屏 + 隐藏系统栏）
