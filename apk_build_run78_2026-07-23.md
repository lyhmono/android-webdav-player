# APK 构建交付 — CI Run #78

> 时间：2026-07-23  
> CI Run: #78 (success)  
> Commit: babcd28 (fix/p0-p3-bugfix-batch)

## 构建内容
- **lite debug APK**：仅 Media3/ExoPlayer 内核，21.29 MB
  - 路径：`apk-output/lite-v2/app-lite-debug.apk`
- **full debug APK**：Media3 + libVLC 双内核，~199 MB
  - 路径：`apk-output/full-v2/app-full-debug.apk`（下载中）

## 本次构建包含的修复
1. P0: DB 版本升级 v2→v3 + Migration（cachedAt 字段）
2. P0: MainActivity 移除手动 startForegroundService
3. P0: Engine ERROR 状态映射修复
4. P1: ExoPlayerEngine 进度轮询空指针保护
5. P1: EngineMedia3Adapter StateSnapshot 原子化（移除冗余 @Volatile）
6. P2: PlayerViewModel ERROR 状态映射
7. P2: PlaybackProgressSaver 节流锁原子性
8. P2: SardineWebDavClient retryIO 协程化（Thread.sleep → delay）
9. P2: PlaylistControllerImpl 全方法 synchronized
10. P3: ServerConfigStore 同步锁
11. P3: 通知渠道 ID 提取为常量
12. P3: RemoteFileDao 去重
13. **cleartext HTTP 流量允许（本次修复）**
    - 添加 `network_security_config.xml`
    - AndroidManifest 添加 `android:networkSecurityConfig`
    - 允许 HTTP 明文通信 + 信任用户证书
