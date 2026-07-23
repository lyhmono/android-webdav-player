# M5 阶段实现总结：字幕/音轨切换 + LibVLC 内核集成 + 引擎动态切换

## 实现日期
2026-07-10

## 目标
在 WebDavPlayer 项目中实现 M5 阶段高级功能：
1. 音轨/字幕切换（ExoPlayer + LibVLC）
2. LibVLC 播放内核集成
3. 引擎动态切换（ExoPlayer ↔ LibVLC）
4. UI 更新（音轨/字幕对话框、控制栏布局调整、引擎选择）

## 新增文件（7 个）

### 1. `core/player/TrackInfo.kt`
- `TrackInfo` 数据类：id, language, title, trackType, isSelected, displayName
- `TrackType` 枚举：AUDIO, SUBTITLE, VIDEO

### 2. `core/player/libvlc/LibVlcPlayerEngine.kt`
- 完整实现 `PlayerEngine` 接口（包括音轨/字幕方法）
- 使用 LibVLC (org.videolan.libvlc) 库
- 通过 HTTP URL 嵌入认证（user:pass@host）实现 WebDAV 流式播放
- LibVLC 事件 → PlaybackState / PlaybackEvent 映射
- 播放速度、音量控制、进度 Flow
- 播放模式（顺序/单曲循环/列表循环/随机）
- 音轨/字幕通过 `MediaPlayer.getTrackDescription()` 实现
- SurfaceView attach/detach 支持

### 3. `core/player/libvlc/LibVlcMediaFactory.kt`
- 将 MediaSource 转换为 LibVLC Media
- 构建带认证的 HTTP URL（URL 嵌入 + HTTP header 备用）
- 设置硬件加速、网络缓存等选项

### 4. `core/player/EngineManager.kt`
- 管理当前活跃播放引擎
- `currentEngine: StateFlow<PlayerEngine>` / `currentEngineType: StateFlow<EngineType>`
- `availableEngines: List<EngineType>` (EXOPLAYER, LIBVLC)
- `switchEngine(type)`: 保存播放状态（队列/索引/位置/模式/播放状态）→ 停止当前引擎 → 切换 → 恢复

### 5. `feature/player/components/AudioTrackDialog.kt`
- 展示可用音轨列表（标题/语言）
- "默认"选项
- 当前选中高亮
- 点击切换

### 6. `feature/player/components/SubtitleTrackDialog.kt`
- 展示可用字幕列表
- "关闭字幕"选项
- "加载外部字幕…"按钮（M5 预留，Toast 提示"功能开发中"）
- 当前选中高亮

### 7. `feature/player/components/LibVlcVideoView.kt`
- AndroidView 封装 LibVLC SurfaceView
- 接收 LibVlcPlayerEngine，attach/detach SurfaceView
- DisposableEffect 管理生命周期

## 修改文件（10 个）

### 1. `core/player/PlayerEngine.kt`
- 新增 `audioTracks: StateFlow<List<TrackInfo>>`
- 新增 `subtitleTracks: StateFlow<List<TrackInfo>>`
- 新增方法：`getAudioTracks()`, `getSubtitleTracks()`, `selectAudioTrack(trackId)`, `selectSubtitleTrack(trackId)`, `disableSubtitle()`

### 2. `core/player/exoplayer/ExoPlayerEngine.kt`
- 添加 `DefaultTrackSelector` 显式创建
- 实现 `onTracksChanged` 回调更新 TrackInfo Flow
- 实现 `getAudioTracks/getSubtitleTracks/selectAudioTrack/selectSubtitleTrack/disableSubtitle`
- 通过 `TrackSelectionOverride` 选择轨道
- 新增 `addExternalSubtitle()` 方法支持外部字幕加载

### 3. `di/PlayerModule.kt`
- 移除 `@Binds` 抽象绑定
- `PlayerEngineFactory` 的 Map 绑定加入 LibVLC
- ExoPlayerEngine 和 LibVlcPlayerEngine 均为 @Singleton @Inject

### 4. `feature/player/PlayerViewModel.kt`
- 注入 `EngineManager`（替代直接注入 ExoPlayerEngine）
- 通过 EngineManager 获取当前引擎操作
- 暴露 `audioTracks` / `subtitleTracks` StateFlow
- 新增 `selectAudioTrack` / `selectSubtitleTrack` / `disableSubtitle` 方法
- 新增 `switchEngine(EngineType)` 方法
- 暴露 `currentEngineType` StateFlow
- `getExoPlayer()` 改为通过 EngineManager 获取
- 新增 `getLibVlcEngine()` 方法

### 5. `feature/player/PlayerScreen.kt`
- 添加音轨/字幕对话框状态
- 根据当前引擎类型渲染视频视图（ExoPlayer: PlayerView, LibVLC: LibVlcVideoView）
- 新增 `onAudioTrackClick` / `onSubtitleClick` 传递给 PlayerControls
- 添加 AudioTrackDialog 和 SubtitleTrackDialog

### 6. `feature/player/components/PlayerControls.kt`
- 底部控制栏新增：音轨按钮 + 字幕按钮
- 三段式布局：左侧(播放模式+音轨+字幕) | 中间(上一首/播放/下一首) | 右侧(速度+播放列表)
- 新增 `onAudioTrackClick` / `onSubtitleClick` 回调参数

### 7. `feature/settings/SettingsScreen.kt`
- ExoPlayer 和 LibVLC 均标注为可用（不再是"即将支持"）
- 点击引擎 RadioButton 时调用切换逻辑
- 添加引擎描述信息
- 仅 SYSTEM 仍标注"即将支持"

### 8. `feature/settings/SettingsViewModel.kt`
- 注入 `EngineManager`
- 暴露 `currentEngineType` StateFlow 和 `availableEngines`
- `setPlayerEngine()` 同时调用 `engineManager.switchEngine()`
- 新增 `switchEngine(EngineType)` 方法

### 9. `gradle/libs.versions.toml`
- 添加 `libvlc = "3.6.0"` 版本
- 添加 `vlc-android` 库定义

### 10. `app/build.gradle.kts`
- 添加 `implementation(libs.vlc.android)` 依赖

### 11. `app/src/main/AndroidManifest.xml`
- MainActivity 添加 `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` 防止视频播放时 Activity 重建

## 技术要点

### LibVLC WebDAV 认证
- URL 嵌入认证：`http://user:pass@host/path`
- 备用 HTTP header：`:http-header=Authorization: Basic <base64>`
- 网络缓存 3000ms，硬件加速 auto

### ExoPlayer 音轨/字幕
- 通过 `DefaultTrackSelector` 的 `TrackSelectionOverride` 选择轨道
- 监听 `onTracksChanged` 回调更新 `audioTracks` / `subtitleTracks` StateFlow
- 支持外部字幕加载（SubtitleConfiguration）

### LibVLC 音轨/字幕
- 通过 `MediaPlayer.getTrackDescription(IMedia.TrackType.Audio/Text)` 获取轨道
- 通过 `MediaPlayer.setAudioTrack(id)` / `setSpuTrack(id)` 选择轨道
- `setSpuTrack(-1)` 关闭字幕

### 引擎切换状态保存/恢复
- 保存：队列、当前索引、播放位置、播放模式、播放状态
- 恢复：setMediaItems → setPlayMode → seekTo → play（如果之前在播放）
