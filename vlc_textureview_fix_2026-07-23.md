# VLC 黑屏修复 + 退出停止 + 全屏 FILL

**日期**: 2026-07-23 05:05  
**Commit**: `39be80f`  
**CI**: ✅ 通过 (run 29957247529)

## 本次修复

### 1. VLC 黑屏根因修复
- **根因**: VlcEngine 用 `SurfaceView(context)` 创建，但 `context` 是 `@ApplicationContext`。Android 的 SurfaceView 必须 attach 到 Activity context 的 Window 才能渲染，ApplicationContext 的 SurfaceView 不会显示画面
- **方案**: 改用 `TextureView`（不依赖 Activity context，用 ApplicationContext 也能正常渲染）
- VlcEngine: `SurfaceView` → `TextureView` + `SurfaceTextureListener`
- `vlcVout.setVideoSurface(Surface, null)` 替代 `vlcVout.setVideoView(SurfaceView)`
- 接口链路全改: `getVlcSurfaceView()` → `getVlcTextureView()`，`vlcSurfaceView` → `vlcTextureView`

### 2. 退出播放界面停止播放
- PlayerViewModel 新增 `stop()` 方法
- PlayerScreen `DisposableEffect(Unit) onDispose` 调用 `playerVm.stop()`
- `stop()`: `mediaController.stop()` + `playerRepository.release()` + 清空 StateFlow

### 3. 全屏 RESIZE_MODE_FILL
- `RESIZE_MODE_FIT` → `RESIZE_MODE_FILL`（视频拉伸填满全屏，不留下方黑边）
- 增加 `LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION | LAYOUT_STABLE` flags

## APK 下载
CI 产物在 GitHub Actions run 29957247529，不自动下载
