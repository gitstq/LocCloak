# locctl

把 [littleRich/VirtualLocation](https://github.com/littleRich/VirtualLocation) 的 **ADB 模拟定位**方案，和 [HotWordland/ChangeLocation](https://github.com/HotWordland/ChangeLocation) 的 **坐标系转换 + GPX 轨迹**思路，重写成一个能在 Windows 上直接对着 ADB 设备跑的 Node CLI。

**零 npm 依赖，只要求装了 `adb` 和 Node ≥ 18。**

```
Node 18+  ·  adb  ·  USB 调试已开启  ·  无需 root
```

开发与实测环境：Redmi 24069RA21C / Android 16 (API 36) / HyperOS / 无 root。

---

## 目录

- [为什么不用原来那两个方案](#为什么不用原来那两个方案)
- [快速开始](#快速开始)
- [命令一览](#命令一览)
- [坐标系](#坐标系)
- [GPX 轨迹回放](#gpx-轨迹回放)
- [原理](#原理)
- [排错](#排错)
- [已知限制](#已知限制)

---

## 为什么不用原来那两个方案

| | VirtualLocation | ChangeLocation | locctl |
|---|---|---|---|
| 形态 | Android APK | iOS + Xcode 工程 | 跨平台 CLI |
| 权限 | 开发者选项手动勾选模拟位置应用 | 无需 | Android 12+ 自动 `appops` 授权 |
| 注入源 | 只 mock `gps` | 无 | `gps` + `network` + `fused` |
| 坐标系 | 无 | GCJ-02 ↔ WGS-84 | WGS-84 / GCJ-02 / BD-09 三方互转 |
| 轨迹 | 无 | 仅模拟器（`.gpx` + Simulate Location） | 任意 GPX 在真机回放 |
| 定位方式 | 单次注入 | — | 常驻推送，规避时间戳校验 |
| 依赖 | APK / Root / Xposed | macOS + Xcode | 无 |

VirtualLocation 的 Hook 方案依赖 root，本机没有 `su`，所以只取了它的 ADB 部分。

---

## 快速开始

```bash
# 1. 自检并自动补权限（只需做一次）
node bin/locctl.js doctor

# 2. 跳到西湖并常驻保持
node bin/locctl.js hold --at 杭州西湖

# 3. 用完还原真实定位
node bin/locctl.js stop
```

多设备时用环境变量指定：

```bash
# Windows PowerShell
$env:LOCCTL_SERIAL = "b615477b"
node bin/locctl.js goto 上海外滩
```

```bash
# macOS / Linux
LOCCTL_SERIAL=b615477b node bin/locctl.js goto 上海外滩
```

---

## 命令一览

| 命令 | 说明 |
|---|---|
| `doctor` | 环境自检，自动补 `appops` 权限、打开定位总开关 |
| `status` | 查看三个 provider 是否被接管、当前注入坐标 |
| `goto <地名>` | 按内置地点跳转（48 个：国内城市地标 + 境外） |
| `set <lat,lng>` / `set <地名>` | 注入任意坐标，或按地名匹配内置地点 |
| `list [关键词]` | 查看内置地点 |
| `hold [lat,lng]` | **常驻循环推送**，`Ctrl+C` 停止 |
| `path <file.gpx>` | 按 GPX 轨迹回放 |
| `wander [lat,lng]` | 在半径内随机漫游 |
| `convert <lat,lng>` | 纯离线坐标系换算，不碰设备 |
| `monitor` | 实时观察设备上报的定位 |
| `stop` | 移除 mock provider，恢复真实定位 |
| `on` / `off` | 系统定位总开关 |

### 全局参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `--cs` | `wgs84` | 输入坐标系：`wgs84` / `gcj02` / `bd09` |
| `--accuracy` | `5` | 上报精度（米） |
| `--providers` | `gps,network,fused` | 接管哪些 provider |
| `--interval` | `500` | 推送间隔（毫秒） |
| `--speed` | `5` | `path` / `wander` 的移动速度（km/h） |
| `--at <地名>` | — | `hold` 的默认地点 |

### 例子

```bash
# 从高德拿到的 GCJ-02 坐标直接注入
node bin/locctl.js set 30.274602,120.211963 --cs gcj02

# 每 300 ms 刷一次，伪装成高精度实时定位
node bin/locctl.js hold 39.9078,116.3912 --interval 300 --accuracy 3

# 以 24 km/h 回放整条西湖环湖轨迹
node bin/locctl.js path routes/west-lake-loop.gpx --speed 24

# 只接管 gps，避开厂商网络定位的干扰
node bin/locctl.js goto 上海外滩 --providers gps

# 在办公楼周围 200 m 随机移动
node bin/locctl.js wander --radius 200
```

---

## 坐标系

设备底层只认 **WGS-84**，而高德、腾讯、微信展示的全是 **GCJ-02**，两者在国内相差 300~600 m。搞错这一点，注入的位置会偏出半个城区。

```bash
node bin/locctl.js convert 30.274602,120.211963 --from gcj02 --to wgs84
```

```
输入          30.2746020, 120.2119630   gcj02
输出(wgs84)   30.2770229, 120.2074175
偏移量         513 m
```

`src/coords.js` 的 `gcj02ToWgs84` 用的是**迭代反解**（原项目是一次近似，误差会累积），往返误差实测 `1.6e-8` m。另外补了 BD-09 互转和 haversine 距离计算。

**规则很简单：注入设备的一律用 WGS-84。** 国内应用会在内部自己转回 GCJ-02。

内置地点同理——国内条目存 GCJ-02（方便和高德对数字），注入前统一转 WGS-84；境外条目直接存 WGS-84。

---

## GPX 轨迹回放

ChangeLocation 的做法是在 Xcode 里放 `.gpx` 然后选 `Debug → Simulate Location`，只能在模拟器上跑。locctl 把 GPX 交给 ADB，真机也能回放。

```bash
node bin/locctl.js path routes/west-lake-loop.gpx --speed 24
```

```
轨迹点         160 个
总里程         8.77 km
速度           24 km/h
预计时长       1316 s
播放 1317 个采样点，约 1316 s，Ctrl+C 结束
```

轨迹点先按里程重采样成等间隔序列，再按固定步长注入，所以 `--speed` 和 `--interval` 可以独立调节。

自带的 `routes/west-lake-loop.gpx` 是一条 8.77 km 的西湖环湖徒步路线（断桥 → 白堤 → 苏堤 → 雷峰塔 → 柳浪闻莺 → 湖滨公园 → 断桥），160 个点。可以用 `npm run gpx` 重新生成。

> GPX 按规范本身就是 WGS-84，所以 `path` 默认 `--cs wgs84`。如果你的 GPX 是别家导出的 GCJ-02，加 `--cs gcj02`。

---

## 原理

```
locctl  ──adb──>  appops set com.android.shell android:mock_location allow
                          │
                   cmd location providers add-test-provider gps|network|fused
                          │
                   cmd location providers set-test-provider-location <p> \
                          --location <lat>,<lng> --accuracy <m> --time <ms>
                          │
                 LocationManagerService
                          │
              gps / network / fused 三个 provider
                          │
                   微信 / QQ / 钉钉 / 高德 ...
```

两个关键实现细节：

**1. 必须接管三个 provider。** 只 mock `gps` 是不够的——国内应用大多走 `network` 或 `fused`，HyperOS 自带的 `com.xiaomi.metoknlp` 还会持续回填网络定位。三个源一起接管才稳定。

**2. 常驻推送，而不是反复起进程。** 推送线程复用一个常驻 `adb shell` 会话，单次开销约 5 ms；如果每 500 ms 起一个 `adb` 进程，开销约 80 ms，跑不动高频。

> 踩过的坑：平台的 `cmd location` **成功时不打印任何内容**，所以常驻会话不能靠「等一行输出」判断完成。实现里统一追加 `; echo rc=$?`，用返回码作为唯一确定的响应。

---

## 排错

### 注入成功但应用位置不变

**这是最常见的问题，绝大多数情况是时间戳过期。**

微信这类应用请求定位时只注册几百毫秒就退出（一次性取最近定位），并会校验定位的 `elapsedRealtime`。一次性注入的坐标放几分钟后会被判定过期而丢弃——`dumpsys location` 里能看到它的 `locations = 0`。

解法是常驻刷新时间戳：

```bash
node bin/locctl.js hold --at 杭州西湖
```

`Ctrl+C` 只停止刷新，不会动已经注入的坐标。

### 怎么确认注入到底有没有生效

```bash
node bin/locctl.js status
```

看到三个 provider 都是「已接管」且坐标正确，再去 `dumpsys location` 里搜目标包名：

```bash
adb shell dumpsys location | grep -A2 "com.tencent.mm"
```

关注 `locations = N` 这个计数——`0` 就说明应用一个定位都没收到。

### `SecurityException: Not allowed to mock location`

Android 12（API 31）起 shell 默认没有 `MOCK_LOCATION` 权限。`doctor` 会自动执行：

```bash
adb shell appops set com.android.shell android:mock_location allow
```

### `LocationManagerService` 连不上 / 命令超时

USB 调试掉了，或有别的 adb 进程占用。`adb devices` 确认设备状态是 `device` 而不是 `unauthorized`。

### 还原后应用还显示旧位置

`stop` 只移除 provider，应用可能缓存了上次的定位。退出应用重进，或重启手机。

---

## 已知限制

- **应用可能检测到模拟定位。** 注入的 provider 归 `com.android.shell` 所有，目标应用调用 `isFromMockProvider()` 会返回 `true`。无 root 的情况下无法处理——这也是 VirtualLocation 要 Hook 的原因，但 Hook 需要 root/Xposed。
- **精度字段不可调。** 平台的 `LocationShellCommand` 只开放纬度、经度、精度、时间四个参数，`bearing` / `speed` / `altitude` 无法通过 shell 注入，所以本工具不提供这三个选项，避免给出无效承诺。
- **重启手机后失效。** mock provider 是运行时状态，不会持久化，需要重新注入。
- **境外坐标不做偏移。** GCJ-02 只在中国境内有效。

---

## 环境变量

| 变量 | 说明 |
|---|---|
| `LOCCTL_SERIAL` | 指定设备（多设备时必填） |
| `LOCCTL_DEBUG` | 打印完整错误堆栈 |
| `NO_COLOR` | 禁用彩色输出 |

---

## 项目结构

```
bin/locctl.js        CLI 入口，参数分发
src/cli.js           命令实现
src/mock.js          provider 注入、状态检测、推送线程
src/adb.js           adb 封装、常驻 shell 会话、设备探测
src/coords.js        坐标系换算与距离计算
src/gpx.js           GPX 解析与重采样
src/presets.js       内置地点库
routes/              示例轨迹
scripts/             GPX 生成、端到端验证
```

零运行时依赖，`npm install` 都不用跑。

---

## 开发

```bash
npm run doctor   # 环境自检
npm test         # 端到端验证（会真实操作已连接设备）
npm run gpx      # 重新生成示例轨迹
```

`npm test` 会对设备执行 `hold` / `path` / `wander` / `stop` 全流程，最后确保设备回到真实定位。**跑之前先关掉自己正在跑的 `hold` 进程。**

---

## 致谢

- [littleRich/VirtualLocation](https://github.com/littleRich/VirtualLocation) — ADB 模拟定位方案
- [HotWordland/ChangeLocation](https://github.com/HotWordland/ChangeLocation) — 坐标系换算与 GPX 轨迹思路

---

## 说明

仅用于开发调试与自动化测试。虚拟定位是 Android 官方提供的测试能力（`adb emu geo fix` / mock location API），使用时请遵守所在组织的规定与目标应用的服务条款。

## License

[MIT](LICENSE)
