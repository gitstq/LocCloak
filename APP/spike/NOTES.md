# Spike 1：Node arm64 在 Android 16 的可用性

**结论：通过。走「内置 Node」路线，不需要回退 Kotlin 移植。**

## 环境

- 设备：Redmi 24069RA21C / Android 16 (API 36) / HyperOS / 无 root
- 来源：Termux 官方仓库 `packages.termux.dev`
- 版本：nodejs `26.4.0-1` aarch64，47.4 MB

## 验证过程

1. 从 Termux apt 索引取到 nodejs 及 7 个依赖（libc++ / openssl / c-ares / libicu / libsqlite / zlib / libffi）
2. 解包 deb，把 `.so` 实体推到 `/data/local/tmp/locctl-spike/`，符号链接在设备上创建（Windows 无法创建 symlink）
3. `LD_LIBRARY_PATH=. ./node -e 'console.log(1+1)'` → 输出 `2`

## 业务代码零改动复用验证

把 `src/coords.js`、`src/gpx.js`、`src/presets.js` 和 `routes/west-lake-loop.gpx` 原样推到设备，用设备上的 Node 直接 require，结果与桌面 CLI **完全一致**：

```
coords gcj02->wgs84: 30.2770229 120.2074175     <- 与桌面一致
gpx points: 160 len: 8.77 km                    <- 与桌面一致
presets: 48 | search 杭州: 杭州西湖              <- 与桌面一致
toPoint 西湖 wgs84: 30.244236 120.141566 | gcj02: 30.241927 120.146295
node version: v26.4.0 | arch: arm64
```

## 性能

| 指标 | 实测 |
|---|---|
| 冷启动 | 229 / 265 / 364 ms |
| 单次 console.log 脚本 | < 100 ms |

冷启动约 300ms，**Node 进程必须设计成长驻**，不能每次 UI 操作都重启。

## ⚠️ 体积：比预估大一倍

| 组件 | 体积 |
|---|---|
| node | 47 MB |
| libicudata.so | 32 MB |
| libcrypto.so.3 | 4.9 MB |
| libicui18n.so | 3.2 MB |
| libicuuc.so | 1.9 MB |
| libc++_shared.so | 1.3 MB |
| libsqlite3.so | 1.1 MB |
| libssl.so.3 | 864 KB |
| 其余 | ~1 MB |
| **合计** | **约 95 MB** |

计划里预估 40-60 MB，实际约 95 MB。**ICU 是硬依赖砍不掉**（实测移除 libicui18n.so.78 后 node 直接 CANNOT LINK）。

## 对后续实现的影响

1. APK 体积约 95 MB（未压缩），需在 README 和商店页明确说明
2. `jniLibs/arm64-v8a/` 放 node + 11 个 `.so`，符号链接需在 App 首启时由 Kotlin 创建（APK 里 jniLibs 不支持 symlink）
3. Node 进程长驻，`NodeBridge` 走 stdio JSON-RPC，UI 层禁止触发重启
4. 桌面 CLI 与 App 共用同一份 `src/*.js`，这一点已得到真实验证

## 附带结论

- nodejs-mobile（专为 App 内嵌设计的构建）未能核实体积——GitHub API 触发 rate limit，官网未列出 Android 下载。若后续需要压缩体积可再评估，但它通常停留在 Node 16/18 且维护不活跃。
- ICU 占 32 MB 是最大单块。若未来接受放弃 `Intl` 能力，可寻找 `--without-intl` 构建，但 Termux 未提供。
