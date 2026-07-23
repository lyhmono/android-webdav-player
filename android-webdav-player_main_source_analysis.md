# Android WebDAV Player — Main 源集完整分析

> 拉取时间: 2026-07-22 18:40 (Asia/Shanghai)
> 仓库: https://github.com/lyhmono/android-webdav-player
> 最新提交: 271a26f — 第四轮优化（时间显示/下拉刷新/空列表引导/NetworkMonitor 防泄漏）

---

## 一、项目架构总览

```
┌──────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                   │
│  AppRoot → NavHost                                        │
│  ├─ ServerListScreen / ServerConfigScreen                  │
│  ├─ BrowseScreen (Paging 3 + Room 缓存)                    │
│  ├─ PlayerScreen (MediaController 客户端)                  │
│  ├─ PlaylistScreen (拖拽重排 + 高亮当前)                    │
│  └─ SettingsScreen                                        │
├──────────────────────────────────────────────────────────┤
│                   ViewModel Layer (Hilt)                   │
│  ServerListVM / ServerConfigVM / BrowseVM                  │
│  PlayerVM / PlaylistVM / SettingsVM                       │
├──────────────────────────────────────────────────────────┤
│                   Domain Layer (Pure Kotlin)               │
│  UseCases: PlayMedia / BrowseDirectory / AddDirVideos      │
│            ManageServer / UploadFile / RenameMoveDelete     │
│            ClearProgress                                    │
│  Interfaces: PlayerEngine / PlaylistController             │
│              PlayerRepository / PlaylistRepository          │
│              BrowseRepository / ServerRepository            │
│              MediaResolver / PlaybackProgressRepository     │
│              SettingsRepository / TrustedCertRepository     │
│  Models: PlaybackState / PlayMode / PlaylistItem           │
│          PlayableMedia / RemoteFile / ServerConfig          │
│          EngineType / EngineListener / MediaType            │
├──────────────────────────────────────────────────────────┤
│                    Data Layer (Hilt @Singleton)           │
│  remote: SardineWebDavClient / CertTrustManager             │
│          WebDavAuthenticator / WebDavPath                    │
│  player: ExoPlayerEngine / VlcEngine(full flavor)           │
│          PlayerEngineFactory / WebDavStreamingSource         │
│  repository: PlayerRepositoryImpl / PlaylistRepositoryImpl  │
│              BrowseRepositoryImpl / PlaylistControllerImpl    │
│              PlaybackProgressSaver / PlaybackProgressRepositoryImpl │
│              ServerRepositoryImpl / SettingsRepositoryImpl   │
│              TrustedCertRepositoryImpl / MediaResolverImpl    │
│  local: AppDatabase / Converters / DAO × 5                 │
│  network: NetworkMonitor                                    │
├──────────────────────────────────────────────────────────┤
│              Service Layer (Media3 MediaSessionService)     │
│  PlaybackService: 后台播放 + 前台通知 + 引擎监听唯一拥有者     │
│  EngineMedia3Adapter: SimpleBasePlayer 代理                 │
│  PlaybackSessionCallback: MediaSession.Callback             │
├──────────────────────────────────────────────────────────┤
│                    DI Layer (Hilt)                         │
│  NetworkModule / DatabaseModule / RepositoryModule          │
└──────────────────────────────────────────────────────────┘
```

### 架构模式
- **Clean Architecture (3 层)**: Domain → Data → UI
- **MVVM**: ViewModel + StateFlow + Compose
- **Hilt** 依赖注入，全部 @SingletonComponent
- **Room** 本地缓存（RemoteFile / Playlist / PlaybackProgress / TrustedCert）
- **Media3 (ExoPlayer)** 默认引擎，**VLC** 可选引擎（full flavor 反射加载）
- **Media3 MediaSessionService** 后台播放 + 系统通知/锁屏控制

---

## 二、核心组件详解

### 2.1 Application 入口 — `WebDavPlayerApp`
- `@HiltAndroidApp` 标注
- `onCreate()` 仅启动 `NetworkMonitor`（全局网络状态监控）
- `onTerminate()` 停止监控
- 遵循"onCreate 不做重活"的冷启动优化原则

### 2.2 MainActivity
- `@AndroidEntryPoint` + `ComponentActivity`
- `enableEdgeToEdge()` — Android 15 edge-to-edge
- 启动 `PlaybackService`（前台服务，承载 MediaSession）
- `setContent { AppRoot() }` — Compose 入口

### 2.3 AppRoot — 导航根
6 个导航目的地:
| 路由 | 屏幕 | 共享 VM |
|------|------|---------|
| `servers` | ServerListScreen | playerVm, playlistVm |
| `server_config?serverId={serverId}` | ServerConfigScreen | — |
| `browse/{serverId}?path={path}` | BrowseScreen | playerVm, playlistVm |
| `player` | PlayerScreen | playerVm, playlistVm |
| `playlist` | PlaylistScreen | playerVm, playlistVm |
| `settings` | SettingsScreen | playerVm, playlistVm |

PlayerViewModel 和 PlaylistViewModel 为 Activity 作用域共享，跨 Browse/Player/Playlist 三个屏幕。

### 2.4 PlaybackService — 后台播放服务
**这是整个项目最核心的组件**，继承 Media3 `MediaSessionService`。

**职责**:
1. 持有 `MediaSession`（背后是 `EngineMedia3Adapter`）
2. 是 `PlayerRepository` 引擎监听的**唯一拥有者**（UI 降级为 MediaController 客户端）
3. 通过 `EngineListener` 驱动会话状态 + 进度落库 + 自然结束续播
4. 应用退后台时自动暂停视频（`ProcessLifecycleOwner` 监听）
5. 通知渠道管理

**引擎监听回调**:
- `onStateChange`: 推送到 adapter + 暂停/结束时 flush 进度
- `onProgress`: 推送到 adapter + 节流保存断点（~5s）
- `onEnded`: 非循环模式清除断点 + 计算下一首 + 自动续播
- `onError`: 状态已由 adapter 推送

### 2.5 EngineMedia3Adapter — Media3 Player 代理
继承 `SimpleBasePlayer`，将 `PlayerRepository`（双内核抽象）适配为 Media3 `Player`:
- `getState()`: 从 PlaylistController 取快照构建播放列表
- `handleSetPlayWhenReady`: 播放/暂停
- `handleSeek`: 跨项 seek → playItem；同项 seek → seekTo
- `handleStop` / `handleRelease`
- 状态映射: PlaybackState ↔ Player.STATE_*
- 自定义 `ImmediateFuture` 替代 Guava（classpath 上只有 listenablefuture 桩）

### 2.6 PlayerRepositoryImpl — 播放控制仓库
- 持有当前 `PlayerEngine` 实例
- `setEngineType`: 应用内切换内核（release 旧 + create 新 + prepare 当前媒体）
- `prepare`: 创建引擎（懒加载）+ 注入共享 OkHttp + prepare 媒体
- 引擎监听由 `PlaybackService` 设置/清除
- `connectFor`: 从 ServerRepository 取配置 → WebDavClient.connect

### 2.7 ExoPlayerEngine — Media3 内核实现
- 内部持有 `ExoPlayer` 实例
- `setOkHttpClient`: 注入共享 OkHttp（含自签信任 + 鉴权）
- `prepare`: 通过 `WebDavStreamingSource` 创建 `ProgressiveMediaSource`
- 进度回调间隔 200ms
- Player.Listener 状态映射

### 2.8 WebDavStreamingSource — 流式数据源
- 复用 WebDavClient 的 OkHttp（含自签信任 + 鉴权）
- `OkHttpDataSource.Factory` → `ProgressiveMediaSource`
- 实现"边下边播、不整文件下载"

### 2.9 PlayerEngineFactory — 双内核工厂
- `MEDIA3` → `ExoPlayerEngine`（默认）
- `VLC` → 反射加载 `VlcEngine`（仅 full flavor 编译）
- lite flavor 无 VLC 时抛出明确错误提示

### 2.10 SardineWebDavClient — WebDAV 客户端
- 基于 Sardine-Android (OkHttpSardine)
- `connect`: 构建 ServerConfig 专属 OkHttpClient（自签信任 + Basic/Digest 鉴权）
  - Basic → 拦截器
  - Digest → Authenticator
  - 自签 → SSLContext + HostnameVerifier
  - 握手失败 → `CertUntrustedException`（携带指纹/颁发者）
- `listDirectory`: PROPFIND Depth:1 → RemoteFile
- `openStream` / `upload` / `rename` / `move` / `delete`
- `getOkHttpClient`: 返回缓存的 OkHttpClient 供流式播放复用

### 2.11 PlaylistControllerImpl — 播放列表导航
- 持有播放列表快照 + 当前索引
- 三种播放模式: SEQUENTIAL / LOOP / SHUFFLE
- `sync`: 从 PlaylistRepository 流同步快照（保留当前播放项索引）
- `onItemEnded`: 等同 `next()`，由 Service 的 onEnded 回调驱动
- SHUFFLE: 随机选取不重复当前项的索引

### 2.12 PlayMediaUseCase — 播放编排
流程: `mediaResolver.resolve(item)` → `playlistController.setCurrent(item)` → `playerRepository.prepare(media)` → 检查断点（>5s 才恢复）→ `seekTo` → `play`

### 2.13 PlaybackProgressSaver — 进度节流保存
- `onProgress`: 每 ~5s 节流落库（fire-and-forget）
- `flush`: 暂停/离开时立即落库（suspend，绕过节流）
- `onEnded`: 非循环模式自然结束时清除断点
- `Mutex` 串行化写操作，防止竞态

### 2.14 PlayerViewModel — 播放页 VM
- **P1 改造**: 降级为 MediaController 客户端
- 不再持有 EngineListener，不再 release 引擎
- 状态/进度来自 MediaController 监听
- 命令优先转发给 MediaController，失败回退到 PlayerRepository 直连
- `playItem`: 调用 PlayMediaUseCase
- `clearProgressAndRestart`: 清库 + seekTo(0) + play

### 2.15 BrowseViewModel — 浏览页 VM
- Paging 3 + Room 缓存分页
- 目录列举: `browseUseCase.refresh` 刷新 Room 缓存
- 长按目录: 识别视频并加入播放列表
- 文件操作: upload / rename / move / delete
- 点击媒体文件: 构建 PlaylistItem → PlayMediaUseCase

### 2.16 DatabaseModule — 数据库
- Room 数据库 `webdav_player.db`
- 5 个 DAO: RemoteFileDao / PlaylistDao / PlaylistMetaDao / PlaybackProgressDao / TrustedCertDao
- Migration v1→v2: 新增 playback_progress 表
- fallbackToDestructiveMigration 兜底

### 2.17 NetworkMonitor
- 全局网络状态监控（ConnectivityManager）
- `isOnline: StateFlow<Boolean>`
- UI 和 Service 均可消费

---

## 三、数据流图

### 播放命令流
```
UI 点击播放
  → PlayerViewModel.playItem(item)
  → PlayMediaUseCase(item)
    → MediaResolver.resolve(item) → PlayableMedia
    → PlaylistController.setCurrent(item)
    → PlayerRepository.prepare(media)
      → PlayerEngineFactory.create(MEDIA3)
      → WebDavClient.connect(config)
      → ExoPlayerEngine.setOkHttpClient(client)
      → ExoPlayerEngine.prepare(media)
        → WebDavStreamingSource.createExoMediaSource(client, media)
        → ExoPlayer.setMediaSource(source)
        → ExoPlayer.prepare()
    → PlaybackProgressRepository.get(serverId, path)
    → (如有断点 > 5s) PlayerRepository.seekTo(position)
    → PlayerRepository.play()
```

### 状态回流
```
ExoPlayer 状态变化
  → ExoPlayerEngine.playerListener
  → EngineListener (PlaybackService.engineListener)
    → EngineMedia3Adapter.onEngineState/onEngineProgress
    → invalidateState()
  → MediaSession 推送给 MediaController
  → PlayerViewModel.playerListener
  → StateFlow 更新 → Compose UI 重组
```

### 后台续播流
```
ExoPlayer STATE_ENDED
  → ExoPlayerEngine.playerListener → listener.onEnded()
  → PlaybackService.engineListener.onEnded()
    → (非 LOOP) PlaybackProgressSaver.onEnded() → 清除断点
    → PlaylistController.onItemEnded() → next()
    → (有下一首) PlayMediaUseCase(next) → 自动续播
```

---

## 四、技术栈总结

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Navigation Compose + Material3 |
| VM | ViewModel + StateFlow + Hilt |
| 分页 | Paging 3 + Room PagingSource |
| 播放 | Media3 (ExoPlayer 1.5.1) + MediaSession + SimpleBasePlayer |
| 备选引擎 | VLC (libVLC, full flavor 反射加载) |
| WebDAV | Sardine-Android (OkHttpSardine) |
| 网络 | OkHttp (共享实例, 自签 SSL, Basic/Digest 鉴权) |
| 数据库 | Room (5 表 + Migration) |
| DI | Hilt (@SingletonComponent + @HiltViewModel) |
| 后台 | MediaSessionService + 前台通知 (mediaPlayback type) |
| 生命周期 | ProcessLifecycleOwner (视频后台暂停) |

---

## 五、项目完成度评估

### 已完成的功能
- ✅ WebDAV 服务器配置管理（CRUD + 选择当前服务器）
- ✅ WebDAV 目录浏览（Paging 3 + Room 缓存 + 下拉刷新）
- ✅ 流式播放（ExoPlayer + 共享 OkHttp + 自签信任 + 鉴权）
- ✅ 双内核支持（Media3 默认 + VLC 可选, 应用内切换）
- ✅ 播放列表管理（增删 + 拖拽重排 + 高亮当前项）
- ✅ 播放模式（顺序 / 循环 / 随机）
- ✅ 播放进度断点恢复（节流保存 ~5s + 暂停 flush + 自然结束清除）
- ✅ 后台播放（MediaSessionService + MediaController 客户端架构）
- ✅ 系统通知/锁屏控制（Media3 DefaultMediaNotificationProvider）
- ✅ 视频后台自动暂停（ProcessLifecycleOwner）
- ✅ 自签证书处理（指纹信任 + CertUntrustedException + 用户确认弹窗）
- ✅ 网络状态监控 + 离线提示
- ✅ 文件管理（上传 / 重命名 / 移动 / 删除）
- ✅ 长按目录批量加入视频到播放列表
- ✅ 文件排序 + 排序方向切换
- ✅ 空列表引导 UI
- ✅ 视频手势层（亮度/音量/进度拖动）
- ✅ 时间显示优化
- ✅ NetworkMonitor 防泄漏

### 可能的待完善项（需要你确认接下来要做什么）
- [ ] 文件搜索功能？
- [ ] 字幕支持？
- [ ] 播放历史记录？
- [ ] 多服务器同时浏览？
- [ ] 缩略图/预览图？
- [ ] 播放速度控制？
- [ ] 音频均衡器？
- [ ] 画中画 (PiP) 模式？
- [ ] Android Auto 支持？
- [ ] Wear OS 支持？
- [ ] 单元测试补充？
- [ ] UI 主题/暗色模式完善？
- [ ] 国际化 (i18n)？
