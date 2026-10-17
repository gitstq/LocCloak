# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## 项目本质

locctl 是**同一套能力的两个交付形态**，共用一份 JS 业务源码：

- **桌面 CLI**（`bin/` + `src/`，零 npm 依赖）—— 通过 `adb shell` 向 Android 设备注入模拟定位
- **Android App**（`APP/`）—— 内置 Node 运行时跑同一份 JS，直接调 `LocationManager` API，脱离电脑

改 `src/coords.js`、`src/gpx.js`、`src/presets.js` 会**同时影响两边**。这三个文件是契约，不是某一方的私有实现。

## 命令

### 桌面 CLI

```bash
node bin/locctl.js doctor                    # 自检 + 自动补 appops 权限
node bin/locctl.js status                    # 三源接管状态与当前坐标
node bin/locctl.js goto 杭州西湖              # 内置地点跳转（单次注入）
node bin/locctl.js hold --at 杭州西湖        # 常驻推送，Ctrl+C 停止
node bin/locctl.js path routes/west-lake-loop.gpx --speed 24
node bin/locctl.js convert 30.274602,120.211963 --from gcj02 --to wgs84
node bin/locctl.js stop                       # 还原真实定位

npm test                                      # 端到端（真机：hold/path/wander/stop 全流程）
npm run gpx                                   # 重新生成示例轨迹
```

`npm test` 会真实操作已连接的设备并最终还原，**跑前先关掉自己正在跑的 `hold` 进程**，否则互相覆盖。多设备用 `LOCCTL_SERIAL` 指定。

### Android

日常迭代用 `run.ps1`，一条命令完成 构建 → 安装 → 启动：

```powershell
cd APP
.\run.ps1                              # 构建+安装+启动          ~7s
.\run.ps1 -SkipBuild                   # 代码没变，只重装启动    ~2s
.\run.ps1 -Log                         # 附加实时日志
.\run.ps1 -Inject "30.244236,120.141566"   # 启动后直接注入，不用点界面
```

等价的手工命令（脚本坏了才需要）：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
cd APP
.\gradlew.bat :app:installDebug --console=plain
adb shell am start -n com.locctl.app/.MainActivity --es inject "30.244236,120.141566"
```

`--es inject "lat,lng"` 是内置的自动化入口（`MainActivity` 的 singleTask onNewIntent），
验证单点功能时用它绕开 UI 点击，比点界面可靠得多。

产物约 105 MB。增量构建约 6s，**不要加 `--no-daemon`**（会退化到 3.5 分钟，见「构建陷阱」）。

## 架构

### 数据流

```
用户操作
  │
  ├─ 桌面 CLI ──adb shell──> cmd location providers set-test-provider-location
  │                              └─> LocationManagerService ─> gps/network/fused ─> 微信/QQ
  │
  └─ Android App ─> InjectionForegroundService (前台服务 + WakeLock)
                        └─> PlaybackEngine (HandlerThread，串行 tick)
                              └─> MockController ─> InjectionChannel
                                                    ├─ SdkMockLocationChannel (主路径)
                                                    └─ ShizukuShellChannel (可选)
  UI 的坐标换算/GPX/检索
                        └─> NodeBridge (stdio JSON-RPC) ─> node 进程
                                                          └─ assets/js/{coords,gpx,presets}.js
```

### 桌面 CLI 分层

`bin/locctl.js` 只做参数分发；`src/cli.js` 是 13 个命令；`src/mock.js` 是注入核心；`src/adb.js` 是 adb 封装。后两层与 Android 端**完全无代码共享**——它们依赖 `child_process` 调 `adb`，在手机上走不通。

`src/adb.js` 的 `openShell()` 复用常驻 `adb shell` 进程 + 请求队列，单次约 5 ms（对比每次起进程约 80 ms）。这是高频推送能跑起来的前提。

### Android 分层

**`core/InjectionChannel`** 是注入通道接口，两个实现。`ChannelProbe` 启动时探测：SDK 可用走 SDK，否则试 Shizuku，都不可用就返回 SDK 通道并在 `reason` 里说清缺什么。**SDK 是主路径**——Spike 2 实测无 root 下拿不到 shell UID，Shizuku 只是给 root 用户或愿意电脑配对一次的用户多一个选择。

**`core/PlaybackEngine`** 在独立 `HandlerThread` 上串行驱动 hold / route / wander 三种模式，状态回调抛回主线程。推送是 Binder 调用，不能放主线程；同时每 tick 只发一次、串行执行，避免息屏后线程池被挂起导致积压。

**`node/NodeBridge`** 走 stdio JSON-RPC 而非 HTTP：不需要 `INTERNET` 权限，不开端口，无网络攻击面。协议是每行一个 JSON 对象，`{id, method, params}` → `{id, ok, result|error}`。

### 职责划分（关键）

| 能力 | 归属 | 频率 |
|---|---|---|
| 常驻推送循环、轨迹回放、随机漫游 | Kotlin `PlaybackEngine` | 高频，每 tick |
| 坐标换算、GPX 解析、地点检索 | Node（三个 JS 模块） | 按需 |
| 随机漫游的半径/角度运算 | Kotlin | 每 tick，十几行不值得跨进程 |

轨迹播放时 Node **一次性**返回重采样后的完整数组，之后由 Kotlin 原生回放，**不做逐点 IPC**。改这里时不要图省事把高频路径也挪进 Node。

## 已验证的硬约束

这些不是设计偏好，是实测结论，改动前先读。

### 三源必须同时接管

只 mock `gps` 对国内应用**完全无效**——微信/QQ/钉钉多走 `network` 或 `fused`，且 HyperOS 的 `com.xiaomi.metoknlp` 会持续回填 network。`ProviderFlags.DEFAULT = [gps, network, fused]`，设置页允许关但默认全开。

### 常驻推送是硬需求，不是可选项

微信请求定位只注册几百毫秒就退出（一次性取最近定位），并校验 `elapsedRealtime`。一次性注入的坐标放几分钟后被判过期丢弃——`dumpsys location` 里能看到微信的 `locations = 0`。**任何「只注入一次」的实现对微信都是无效的。**

### `cmd location` 成功时不输出任何内容

shell 会话不能靠「等一行输出」判断完成，那会每次都等到超时。`ShellSession.sendChecked()` 和 `NodeBridge` 侧统一追加 `; echo rc=$?`，用返回码作为唯一确定的响应行。

### `dumpsys location` 输出是 CRLF

JS 正则的 `.` 不匹配 `\r`，解析前必须 `replace(/\r\n/g, "\n")`。Kotlin 侧同理。

### 坐标系

设备底层只认 **WGS-84**。内置地点国内条目存 GCJ-02（方便和高德对数字），注入前统一转 WGS-84；境外直接存 WGS-84。`gcj02ToWgs84` 用迭代反解，往返误差 `1.6e-8` m。

## 构建陷阱（都踩过）

**编译「假死」在 `checkDebugAarMetadata`** —— 不是死锁，是直连 `dl.google.com` 时 TLS 握手被中断，Gradle 静默重试。`settings.gradle.kts` 已配阿里云镜像。

**不要用 `--no-daemon`** —— 早期为绕开 daemon 锁竞争加过，代价是每次冷启动 JVM + 重新配置工程，慢 30 倍。现在靠「配置缓存 + 定期清理僵尸 daemon」解决：正常构建用 daemon，卡住时先 `.\gradlew.bat --stop` 再重试。真正的锁竞争来源是 `~/.gradle/caches` 下多个 Gradle 版本（8.5/8.11.1/9.3.1/9.7.1）共存，不是 daemon 本身。

**判断构建是否正常**：输出里出现 `Configuration cache entry reused` 说明走了快路径；每次都从 `> Configure project` 开始就说明配置缓存没生效，检查是不是误加了 `--no-daemon` 或改了 gradle 配置。

**中文路径** —— 仓库路径含中文，AGP 默认拒绝构建，`gradle.properties` 里已开 `android.overridePathCheck=true`（本项目不用 NDK 编译，风险面比常规工程小）。

**minSdk 是 31，不是 26** —— API 36 已移除 `Criteria.POWER_REQUIREMENT_*`，废弃的 `addTestProvider(String, boolean, ...)` 重载无法编译，只能走 `ProviderProperties`（API 31+）。

**`ProviderProperties.Builder` 的方法名是 `setHasAltitudeSupport` / `setHasSpeedSupport` / `setHasBearingSupport`**，不是 `setSupportsAltitude`。用 `javap -classpath $ANDROID_HOME/platforms/android-36/android.jar <FQCN>` 确认签名，别猜。

**`Shizuku.newProcess` 在 api 13.1.5 是 private** —— 公开 API 没有创建进程的入口。`ShizukuShellChannel` 用反射调用，反射不到就报「通道不可用」并静默回退 SDK。这是有意的降级，不是待修的 bug。

## Node 运行时（Android 端）

`jniLibs/arm64-v8a/` 里是 Termux 预编译的 node 26.4.0 + 11 个 `.so`，约 93 MB（node 47 MB + libicudata 32 MB 是大头，ICU 是硬依赖砍不掉）。

三个要点：

1. **APK 的 jniLibs 不支持符号链接**，而 node 依赖 `libz.so.1 → libz.so.1.3.2` 这类 SONAME 链接。`NodeRuntime.install()` 首启时把 `.so` 复制到 `filesDir/node/lib/`，再在设备上 `ln -sf` 建链。**Windows 上无法创建符号链接**，所以这步只能在设备上做。
2. **`node` 二进制在 jniLibs 里叫 `libnode.so`**，复制到 filesDir 后 `setExecutable(true)`。
3. **assets 里的 js 不能直接 `require`**（assets 不是真实文件路径、不可写）。`NodeBridge.extractScript()` 首启时把 `assets/js/` 解到 `filesDir/js/`。

Node 冷启动约 300 ms，所以进程必须长驻。**UI 层绝不能触发 node 重启**——所有计算都走 `NodeBridge.call()`，由 `LocctlApp` 持有单例。

`assets/js/` 里的三个业务文件是 `src/` 的**副本**，不是软链。改完 `src/coords.js` 记得同步：

```bash
Copy-Item src/coords.js,src/gpx.js,src/presets.js APP/app/src/main/assets/js/ -Force
```

## 环境与验证

- 实测设备：Redmi 24069RA21C / Android 16 (API 36) / HyperOS / **无 root**
- `APP/spike/` 下两份 NOTES 记录了 Node 运行时和 shell UID 可行性的验证过程与结论，改架构前先读
- 装 APK 需要手机开发者选项开启「通过 USB 安装」，否则 `adb install` 报 `INSTALL_FAILED_USER_RESTRICTED`
- 真机调试时 `adb shell dumpsys location` 看 provider 是否 `[mock]`、目标应用的 `locations = N` 计数——这是判断注入是否真的送达的唯一可靠手段
