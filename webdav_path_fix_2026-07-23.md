# WebDAV 路径重复修复 — CI Run #80

> 时间：2026-07-23 02:15  
> CI Run: #80 (success)  
> Commit: 3893570 (fix/p0-p3-bugfix-batch)

## 问题
用户连接 AList WebDAV（`http://127.0.0.1:5244/dav`）后浏览目录报 404：
`http://127.0.0.1:5244/dav/dav/天翼云` — 路径中 `/dav` 重复了。

## 根因
AList WebDAV 的 PROPFIND 响应中，子目录的 href 包含完整路径前缀（如 `/dav/天翼云`）。
`mapResource` 直接从 href 末段提取名称，但 AList 根目录返回的列表中包含一个名为 `dav` 的
"虚拟目录"（实际是 baseUrl 的路径前缀），用户点击后路径变成 `/dav`，再点子目录就变成
`/dav/天翼云`，拼接 baseUrl 后 → `/dav/dav/天翼云` → 404。

## 修复
`SardineWebDavClient.mapResource` 增加 `basePath` 参数：
- 从 `cfg.baseUrl` 中用 `java.net.URI` 提取路径前缀（如 `/dav`）
- 从资源 href 中剥离该前缀，只保留相对路径
- 取相对路径末段作为文件名

## 文件变更
- `SardineWebDavClient.kt` — `listDirectory` 提取 basePath，`mapResource` 接收并剥离

## CI
- Run #79: 失败（`HttpUrl.parse(String)` 弃用 error）
- Run #80: 通过（改用 `java.net.URI`）
