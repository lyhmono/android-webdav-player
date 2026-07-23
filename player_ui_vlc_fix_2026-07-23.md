# 播放器 UI 重写 + 全屏模式 + VLC Surface 修复

**日期**: 2026-07-23  
**Commit**: `35d884a` (on `fix/p0-p3-bugfix-batch`)  
**CI**: ✅ 通过 (lite + full 双 APK 构建成功)

## 本次修复内容

### 1. PlayerScreen 重写（删除冗余进度条）

**问题**: PlayerView 自带控制器已有进度条，外面又包了一个 Compose `Slider`，导致两个进度条共存。

**修复**:
- 移除非全屏模式下的冗余 Compose `Slider`
- 非全屏模式：使用 `PlayerView` 自带的控制器（`useController = true`）
- 全屏模式：使用自定义 Compose 控制层（进度条 + 播放/暂停 + 时间显示）
- 视频区域用 `aspectRatio(16:9)` 替代固定 240dp 高度

### 2. 全屏模式

**问题**: 之前只有全屏按钮切换状态，没有真正实现全屏。

**修复**:
- 强制横屏 (`ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE`)
- 沉浸式 SystemUI 隐藏（状态栏 + 导航栏）
- `FLAG_KEEP_SCREEN_ON` 保持屏幕常亮
- 点击屏幕切换控制层显示/隐藏（淡入淡出动画）
- 自定义底部控制栏：进度条 + 播放/暂停 + 当前时间/总时长
- 顶部退出全屏按钮

### 3. VLC 视频渲染修复

**问题**: `VlcEngine` 完全没有设置视频 Surface，libVLC 无法渲染画面。

**修复**:
- `VlcEngine` 新增 `surfaceView: SurfaceView` 属性
- `SurfaceHolder.Callback`:
  - `surfaceCreated` → `vlcVout.setVideoView(surfaceView)` + `attachViews()`
  - `surfaceChanged` → `setWindowSize(width, height)`
  - `surfaceDestroyed` → `detachViews()`
- `release()` 中先 `detachViews()` 再 release
- 构造函数添加 `"--fullscreen"` 选项

### 4. PlayerRepository 接口扩展

- 新增 `getVlcSurfaceView(): SurfaceView?`
- `PlayerRepositoryImpl` 通过反射获取（兼容 lite flavor 无法引用 `VlcEngine` 类）

### 5. PlayerViewModel 扩展

- `vlcSurfaceView: StateFlow<SurfaceView?>` 暴露给 UI
- `playItem` 和 `switchEngine` 后同步更新 `exoPlayer` / `vlcSurfaceView`

### 6. PlayerScreen 双内核渲染

- 非全屏：VLC 模式挂载 `SurfaceView`，Media3 模式使用 `PlayerView`
- 全屏：同样根据引擎类型选择渲染方式

## 构建产物

| APK | 大小 | 路径 |
|-----|------|------|
| Lite (Media3 only) | 22 MB | `downloaded-apks/app-lite-debug/app-lite-debug.apk` |
| Full (Media3 + VLC) | 209 MB | `downloaded-apks/app-full-debug/app-full-debug.apk` |

## 修改文件

1. `app/src/main/java/.../ui/player/PlayerScreen.kt` — 完整重写
2. `app/src/main/java/.../ui/player/PlayerViewModel.kt` — 添加 vlcSurfaceView
3. `app/src/main/java/.../domain/repository/PlayerRepository.kt` — 添加 getVlcSurfaceView 接口
4. `app/src/main/java/.../data/repository/PlayerRepositoryImpl.kt` — 反射实现
5. `app/src/full/java/.../data/player/VlcEngine.kt` — SurfaceView + SurfaceHolder.Callback

## 待验证

- [ ] 实机测试 VLC 内核视频播放
- [ ] 实机测试全屏模式沉浸式 SystemUI
- [ ] 实机测试点击切换控制层显示/隐藏
