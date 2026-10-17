@echo off
chcp 65001 >nul
title locctl 虚拟定位控制台
cd /d "%~dp0"

echo.
echo ======================================================
echo   locctl · Android 虚拟定位控制台 (桌面图形版)
echo   内置 ADB 驱动 · 零依赖秒开 · 自动识别真机
echo ======================================================
echo.

where node >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Node.js 环境！
    echo 请先安装 Node.js (推荐 v18 及以上版本): https://nodejs.org/
    echo.
    pause
    exit /b 1
)

echo 正在启动 Web/桌面 UI 服务并自动打开页面...
echo 如果页面没有自动弹出，请手动在浏览器访问终端打印的地址。
echo.

node bin\locctl.js ui

pause
