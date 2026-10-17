# locctl 项目全局研发与发版规则规范 (Project Rules)

> 本文档为 `locctl` 项目的最高级别研发规范与发版标准，所有后续的功能开发、版本迭代、Agent 交互与发布流程**必须严格遵守**。

---

## 1. 软件版本号与发版规范 (Versioning & Release Rules)

### 1.1 语义化版本号格式 (SemVer)
- 项目统一遵循 `vX.Y.Z` 语义化版本规范：
  - `X` (Major): 重大架构变动或重构（如原生化、多端重构）；
  - `Y` (Minor): 新增功能特性（如新页面、新注入模式、地址库）；
  - `Z` (Patch): 缺陷修复与微调（如 UI 调整、崩溃修复）。
- **版本号强一致性要求**：
  - Android APP 的 `APP/app/build.gradle.kts` 中 `versionName` 必须与 GitHub Release Tag 严格对齐（如 `versionName = "2.1.0"` 对应 Tag `v2.1.0`）。
  - `versionCode` 必须按 `X * 10000 + Y * 100 + Z` 保持单调递增（如 `v2.1.0` 对应 `20100`）。
  - 禁止在界面代码中硬编码版本字符串，必须且仅能通过 `BuildConfig.VERSION_NAME` 动态获取。

### 1.2 GitHub Releases 资产发布规范
- 每次在 GitHub 发布新版本时，必须打包并上传符合以下命名规范的通用安装包：
  1. **`Locctl-vX.Y.Z-android-universal.apk`**（首选标准通用包，包含 arm64-v8a / armeabi-v7a / x86_64 架构）；
  2. **`Locctl-vX.Y.Z-android.apk`**（别名安装包，便于快速分发）。
- **Release Body 规范**：每次发版必须在 Release Notes 中提供完整的更新日志、核心变更点与快速使用说明。

---

## 2. 真实在线软件更新机制规范 (Software Update Mechanism)

### 2.1 真实联网检测要求（严禁静态 Mock）
- **绝不允许**使用 `delay(600)` 等静态延时弹窗假冒更新检测。
- 软件内所有的“检查更新”入口必须统一由 `com.locctl.app.update.UpdateManager` 处理，向 GitHub API 发起真实的 HTTP 异步网络交互。

### 2.2 协议与接口标准
- **接口地址**：`GET https://api.github.com/repos/gitstq/locctl/releases/latest`
- **请求头配置**：
  - `User-Agent: locctl-android-client`
  - `Accept: application/vnd.github.v3+json`
  - 超时时间设定：连接超时 ≤ 8000ms，读取超时 ≤ 8000ms。
- **字段解析与下载策略**：
  - 解析 `tag_name` 作为远端最新版本号；
  - 遍历 `assets` 列表，优先筛选匹配包含 `universal` 或 `android` 的 `.apk` 文件直链 `browser_download_url`；
  - 若 Release 中未挂载 APK 附件，自动降级为使用 `html_url`（GitHub Release 网页发布页）；
  - 解析 `body` 作为更新日志说明，解析 `published_at` 作为发版时间。

### 2.3 版本比对算法
- 比较本地 `BuildConfig.VERSION_NAME` 与远端 `tag_name`：
  - 剥除前后缀字符（如 `v`, `V`, 空格及 `-beta` 等标签）；
  - 采用分段数字数组逐位比对（`Major -> Minor -> Patch`），**严禁**使用简单的字符串字典序比较，避免 `2.10.0` 比 `2.9.0` 小的逻辑错误。

### 2.4 用户交互与容错规范
- **检测中状态**：提供明确的 Loading 旋转微标与“检查中...”禁用状态，防止重复并发请求；
- **发现新版本**：弹窗展示最新版本号、发布日期、可滚动的发版日志，并提供【立即下载新版】（一键调用系统浏览器触发直链下载）；
- **已是最新版**：友好提示“当前已是最新正式版本”；
- **网络异常/超时/限流容错**：严禁静默崩溃，必须捕获异常并弹窗解释网络状况，提供【打开发行页面】按钮直接跳转官方网页手动下载。

---

## 3. UI 与交互设计规范 (UI & Interaction Rules)

### 3.1 悬浮独立圆形按钮规范
- 底部 Tab 导航栏必须保持**完全悬浮在页面底部的独立圆形按钮（CircleShape）**，彼此之间必须由 `Arrangement.SpaceEvenly` 空白透明隔开，严禁回退为整条铺满的实心底座通栏。
- 页面列表内容底部必须预留至少 `100.dp` 的安全内边距（`contentPadding`），防止滑动到底部时内容被悬浮按钮遮挡。

### 3.2 图标库使用规范
- 项目统一使用 `compose.icons.FeatherIcons` 现代流线型矢量图标，严禁混用风格不协调的原生 Material 粗糙图标。

---

## 4. 自动化管道与命令行工具规范 (CLI & Pipeline Rules)

### 4.1 ripgrep (`rg`) 命令规则
- 在自动化流水线、非交互管道或工具调用（如 `run_command`）中执行 `rg` 时，**必须显式提供目标路径参数**（例如 `rg "<pattern>" .` 或 `rg "<pattern>" <指定目录>`），严禁省略目标路径导致标准输入阻塞挂起。

### 4.2 构建与打包规则
- 编译 Release APK 必须在设置 `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"` 后执行 `.\gradlew.bat assembleRelease`。
- 打包输出后必须同步复制至 `APP/dist/` 进行版本归档，并通过 `gh release` 同步推送到远端仓库。
