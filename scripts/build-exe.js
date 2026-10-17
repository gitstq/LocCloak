'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ROOT_DIR = path.join(__dirname, '..');
const DIST_DIR = path.join(ROOT_DIR, 'dist');
const CSC_PATH = 'C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe';

if (!fs.existsSync(DIST_DIR)) {
  fs.mkdirSync(DIST_DIR, { recursive: true });
}

console.log('==========================================');
console.log('  开始构建 locctl Windows 可执行程序 (.exe)');
console.log('==========================================');

// 1. 编译根目录直接运行的 隐影斗篷.exe
console.log('\n[1/3] 编译根目录绿色版 隐影斗篷.exe / LocCloak.exe...');
const launcherCs = path.join(ROOT_DIR, 'scripts', 'launcher.cs');
const outCloakExe = path.join(ROOT_DIR, '隐影斗篷.exe');
const outGreenExe = path.join(ROOT_DIR, 'LocCloak.exe');
const outGreenLocctl = path.join(ROOT_DIR, 'locctl.exe');

const cmdGreen = `"${CSC_PATH}" /nologo /target:winexe /out:"${outCloakExe}" /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll "${launcherCs}"`;
execSync(cmdGreen, { stdio: 'inherit' });

fs.copyFileSync(outCloakExe, outGreenExe);
fs.copyFileSync(outCloakExe, outGreenLocctl);
const greenStat = fs.statSync(outCloakExe);
console.log(`  -> 成功生成: 隐影斗篷.exe 与 LocCloak.exe (${(greenStat.size / 1024).toFixed(1)} KB)`);

// 2. 将核心资源打包为临时 bundle.zip
console.log('\n[2/3] 打包内置运行组件 (Node + 内置 ADB + Web UI + 预设)...');
const tempZip = path.join(process.env.TEMP || '.', 'locctl_bundle.zip');
if (fs.existsSync(tempZip)) fs.unlinkSync(tempZip);

// 使用 powershell Compress-Archive 压缩
const compressCmd = `powershell -NoProfile -Command "Compress-Archive -Path 'bin','src','routes','package.json','README.md' -DestinationPath '${tempZip}' -CompressionLevel Optimal"`;
execSync(compressCmd, { cwd: ROOT_DIR, stdio: 'inherit' });

const zipStat = fs.statSync(tempZip);
console.log(`  -> 核心套件已压缩: ${(zipStat.size / (1024 * 1024)).toFixed(2)} MB`);

// 3. 将 bundle.zip 嵌入编译为真正的单文件独立版
const pkg = require('../package.json');
console.log(`\n[3/3] 编译自包含单文件独立版 dist/LocCloak-v${pkg.version}-windows-x64.exe...`);
const outSingleExe = path.join(DIST_DIR, `LocCloak-v${pkg.version}-windows-x64.exe`);
const outCloakAlias = path.join(DIST_DIR, '隐影斗篷-便携单文件版.exe');
const outDesktopAlias = path.join(DIST_DIR, 'locctl-desktop-windows-x64.exe');

const cmdSingle = `"${CSC_PATH}" /nologo /target:winexe /out:"${outSingleExe}" /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /resource:"${tempZip}",bundle.zip "${launcherCs}"`;
execSync(cmdSingle, { stdio: 'inherit' });

fs.copyFileSync(outSingleExe, outCloakAlias);
fs.copyFileSync(outSingleExe, outDesktopAlias);

// 清理临时 zip
try { fs.unlinkSync(tempZip); } catch {}

const singleStat = fs.statSync(outSingleExe);
console.log(`  -> 成功生成单文件独立版: dist/LocCloak-v${pkg.version}-windows-x64.exe (${(singleStat.size / (1024 * 1024)).toFixed(2)} MB)`);

console.log('\n==========================================');
console.log('  全部构建完成！已提供两种形态：');
console.log('  1. 根目录直接运行: 隐影斗篷.exe / LocCloak.exe (当前文件夹双击即开，0秒启动)');
console.log(`  2. 单文件独立发行版: dist/LocCloak-v${pkg.version}-windows-x64.exe (便携独立单文件)`);
console.log('==========================================');
