# WebDAV 媒体播放器 Android 应用 — 技术方案

## 基本信息
- 日期: 2026-07-10
- 平台: Android 10 (API 29) +
- 技术栈: Kotlin + Jetpack Compose + Material Design 3
- 架构: Clean Architecture + MVVM

## 用户需求
- 音视频播放器，连接 WebDAV 服务器
- 音频覆盖常见格式，视频格式尽可能全面
- 三播放内核可选: ExoPlayer(默认) / LibVLC / MX Player Core
- 投屏预留接口，第一版不实现
- 文件管理: 上传、编辑(重命名)、删除
- 播放列表: 长按目录加入，自动识别视频文件；顺序/循环/随机
- 自签证书支持
- 冷启动，无应用锁
- 中文界面
- 大目录性能优化

## 技术方案要点

### 播放内核
- 统一 PlayerEngine 接口，三内核分别实现
- ExoPlayer: Media3 + 自定义 WebDavDataSource (Range请求)
- LibVLC: libvlc-all，全格式覆盖
- MX Player Core: 第三方 SDK
- 首次启动选择内核，设置中可切换

### WebDAV 客户端
- OkHttp 封装，自签证书通过自定义 TrustManager
- 支持 Basic/Digest Auth
- PROPFIND/GET/PUT/MOVE/DELETE 方法

### 大目录优化 (四层)
1. 分页 PROPFIND (客户端分页，LazyColumn)
2. 目录结构 SQLite 缓存，二次进入先显缓存后刷新
3. 缩略图按需生成，Coil LRU+磁盘缓存
4. DTO 轻量对象，目录切换清理引用

### 播放列表
- Room 存储，支持多个列表
- 长按目录 → 异步扫描媒体文件 → 加入列表
- 播放模式: 顺序/列表循环/单曲循环/随机

### 冷启动
- 自动重连上次服务器
- 骨架屏 + 缓存优先展示
- 目标首屏可交互 < 800ms

## 功能优先级
- P0: WebDAV连接、文件浏览器、视频播放(三内核)、音频播放、播放列表、文件上传/删除/重命名
- P1: 多服务器管理、缩略图预览、播放位置记忆
- P2: 投屏、外挂字幕、倍速播放

## 里程碑 (约7.5周单人开发)
- M1: 基础架构 + WebDAV客户端 (1周)
- M2: 文件浏览器 (1.5周)
- M3: 播放内核 (2周)
- M4: 播放列表 (1周)
- M5: 文件管理 (1周)
- M6: 打磨测试 (1周)

## 关键依赖
- Media3 (ExoPlayer) 1.4.x
- libvlc-all 3.6.x
- OkHttp 4.12.x
- Room 2.6.x
- Coil 2.7.x
- Hilt 2.52.x
- Compose Material3 1.3.x
