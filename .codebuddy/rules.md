# locctl 项目开发与发版更新规则规范

参见项目根目录 [PROJECT_RULES.md](../PROJECT_RULES.md)。

## 核心必须遵守原则：
1. **禁止 AI 自行验证 APP，必须人工手动测试**：严禁 AI 助手使用 `adb screencap`、`uiautomator dump`、`input tap`、录屏或自动化脚本自行抓屏验证功能。编译并安装到手机后必须立即交付，由人工手动在真机上测试体验，严禁在目录中生成任何截图或临时 dump 文件。
2. **真实软件更新机制**：所有检查更新必须由 `UpdateManager.checkForUpdates` 发起真实的 GitHub Releases API (`https://api.github.com/repos/gitstq/locctl/releases/latest`) 网络请求，严禁使用任何 `delay()` 假冒检测。
3. **版本号统一**：`build.gradle.kts` 的 `versionName` 必须与 GitHub Release Tag (SemVer) 严格一致。
4. **资产命名标准**：每次发布 Release 必须上传 `Locctl-vX.Y.Z-android-universal.apk` 资产。
5. **UI 悬浮独立圆形按钮**：底部导航栏必须保持悬浮在页面底部的独立圆形按钮（CircleShape），中间空白隔开。
6. **rg 命令规则**：任何非交互工具执行 `rg` 必须显式携带目标路径参数。
