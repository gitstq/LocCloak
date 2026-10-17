#!/usr/bin/env pwsh
# 快速迭代脚本：一条命令完成 构建 -> 安装 -> 启动 -> 看日志
#
#   .\run.ps1                 # 构建+安装+启动
#   .\run.ps1 -SkipBuild      # 代码没变，只重新安装启动
#   .\run.ps1 -Log            # 附加实时日志（Ctrl+C 退出）
#   .\run.ps1 -Inject "39.9078,116.3912"   # 启动后直接注入某个坐标
#
# 依赖环境变量（脚本会自己设默认值，通常不用管）：
#   JAVA_HOME    C:\Program Files\Java\jdk-17
#   ANDROID_HOME $env:LOCALAPPDATA\Android\Sdk

param(
    [switch]$SkipBuild,
    [switch]$Log,
    [string]$Inject
)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Java\jdk-17" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }

$root = Split-Path -Parent $PSCommandPath
Set-Location $root

$pkg = "com.locctl.app"
$act = "$pkg/.MainActivity"

if (-not $SkipBuild) {
    Write-Host "==> 构建并安装（增量）" -ForegroundColor Cyan
    # 用输出判断成功，而不是 $LASTEXITCODE —— gradlew.bat 走 endlocal，
    # 退出码在 PowerShell 里不可靠，会误报失败
    $out = & .\gradlew.bat :app:installDebug --console=plain 2>&1 | Out-String
    if ($out -notmatch "BUILD SUCCESSFUL") {
        Write-Host $out -ForegroundColor Red
        exit 1
    }
    Write-Host "   构建成功" -ForegroundColor Green
}

# 启动。若给了坐标，用 Intent extra 直接触发注入，省掉手动点界面
if ($Inject) {
    Write-Host "==> 启动并注入 $Inject" -ForegroundColor Cyan
    adb shell am start -n $act --es inject "$Inject" | Out-Null
} else {
    Write-Host "==> 启动" -ForegroundColor Cyan
    adb shell am start -n $act | Out-Null
}

if ($Log) {
    Write-Host "==> 日志（Ctrl+C 退出）" -ForegroundColor Cyan
    adb logcat -c
    adb logcat -v color | Select-String -Pattern "locctl|AdbManager|AdbPair|AdbClient|InjectionService"
}
