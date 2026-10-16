# Spike 2：无电脑获取 shell UID 的可行性

**结论：Shizuku / shell UID 通道在本机不可行，不应作为主路径。SDK 通道是唯一能保证「完全单机」的方案。**

## 实测过程与发现

### 1. 无线调试无法从 shell 开启（硬约束）

```
adb shell settings put global adb_wifi_enabled 1   → 静默失败，读回仍为 0
adb shell cmd adbd                                 → Can't find service: adbd
adb shell cmd adb is-wifi-supported                → true
adb shell cmd adb is-wifi-qr-supported             → true
```

HyperOS 忽略 `adb_wifi_enabled` 的 shell 写入。`cmd adb` 只暴露了两个只读查询，没有启动 adbd 的入口。**用户必须手动进开发者选项打开「无线调试」。**

### 2. 配对机制在设计上就需要两个设备

Android 11+ 的无线调试配对只有两种形式：

- 「使用配对码配对设备」：本机显示 IP:端口 + 6 位码，**另一台设备**输入
- 「配对二维码」：本机显示二维码，**另一台设备**扫描

两种都是「本机 + 另一台设备」的双设备模型，**没有为自身配对的入口**。让 App 同时扮演配对双方属于非预期用法。

### 3. 自配对可行性无法在本机完成实测

推进自配对需要用户手动操作手机 UI（开无线调试、点配对、读码），无法用 adb 代劳；且即使完成，还受路由器 hairpin NAT 影响（设备经局域网 IP 回连自身，多数家用路由器不支持）。**不保证可行。**

## 关键论证：为什么 SDK 通道严格优于 Shizuku 通道

在「必须完全单机」这个约束下对比两条路的代价：

| | Shizuku / shell UID | SDK 模拟定位 |
|---|---|---|
| 首次需要什么 | 开无线调试 + 点配对 + 读 6 位码 + 输入 App + 自配对（不确定能成） | 开发者选项里把本 App 选为「模拟位置应用」 |
| 每次开机后 | **要重新配对一次**（无 root 下 Shizuku 不能可靠自启） | 选过就一直在，重装才需重选 |
| 可行性 | 不确定 | 确定 |
| 需要电脑 | 设计上需要 | **完全不需要** |
| 能否设置 bearing/speed/altitude | 不能（shell 接口只开放 4 个参数） | **能**（`setTestProviderLocation` 传完整 Location） |

**两条路的成本都是「开发者选项里的一次手动操作」，但 SDK 通道步骤更少、确定可行、且不需要 shell UID。**

结论：Shizuku 通道唯一的理论收益（不必把 App 选为模拟位置应用）并不值得用「每次开机重新配对 + 自配对不确定」去换。

## 对计划的影响

- **`SdkMockLocationChannel` 是主路径**，必须做扎实——它是「完全单机」的唯一保证
- **`ShizukuShellChannel` 保留但降级为可选增强**：对 root 用户、或愿意用电脑配对一次的用户仍有价值。`ChannelProbe` 探测不到 shell UID 时静默走 SDK，UI 明确告知当前生效通道
- 计划中 `sdk-channel` 无依赖、`shizuku-channel` 依赖 `sdk-channel` 的顺序不变，SDK 优先落地
- 计划中「Shizuku 优先、SDK 降级」的表述应反过来：**SDK 优先、Shizuku 降级**

## 设备状态

实测中 `adb_wifi_enabled` 已恢复为 0（原本就是 0），未对设备做持久改动。
`/data/local/tmp/locctl-spike/` 保留，供后续真机调试复用。
