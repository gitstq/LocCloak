'use strict';

const http = require('http');
const { startServer } = require('../src/server');

async function request(url, options = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method: options.method || 'GET',
        headers: options.headers || {},
      },
      (res) => {
        let data = '';
        res.on('data', (c) => (data += c));
        res.on('end', () => {
          resolve({ status: res.statusCode, headers: res.headers, body: data });
        });
      }
    );
    req.on('error', reject);
    if (options.body) req.write(options.body);
    req.end();
  });
}

async function runTests() {
  console.log('--- 开始自测 UI 页面与 API 服务 ---');
  const port = 8999;
  const { server, url } = await startServer({ port, open: false });
  console.log(`UI 服务已在 ${url} 启动`);

  try {
    // 1. 测试静态页面根目录
    console.log('1. 测试根页面 GET /');
    const rRoot = await request(`${url}/`);
    if (rRoot.status !== 200 || !rRoot.body.includes('locctl · Android 虚拟定位控制台')) {
      throw new Error(`根目录响应异常: HTTP ${rRoot.status}`);
    }
    console.log('   [OK] 静态主页加载正常，长度:', rRoot.body.length);

    // 2. 测试 Leaflet 本地静态资源
    console.log('2. 测试 Leaflet 静态文件 GET /vendor/leaflet/leaflet.js');
    const rLeaflet = await request(`${url}/vendor/leaflet/leaflet.js`);
    if (rLeaflet.status !== 200 || rLeaflet.body.length < 50000) {
      throw new Error(`Leaflet 静态文件异常: HTTP ${rLeaflet.status}`);
    }
    console.log('   [OK] 本地内置 Leaflet.js 正常，长度:', rLeaflet.body.length);

    // 3. 测试 /api/status
    console.log('3. 测试状态接口 GET /api/status');
    const rStatus = await request(`${url}/api/status`);
    const statusJson = JSON.parse(rStatus.body);
    if (!statusJson.ok || !statusJson.adb.path) {
      throw new Error('Status API 返回异常: ' + rStatus.body);
    }
    console.log('   [OK] Status 正常，内置 ADB:', statusJson.adb.builtin, '设备数:', statusJson.devices.length);

    // 4. 测试 /api/presets
    console.log('4. 测试预设地点 GET /api/presets');
    const rPresets = await request(`${url}/api/presets`);
    const presetsJson = JSON.parse(rPresets.body);
    if (!presetsJson.ok || presetsJson.presets.length < 10) {
      throw new Error('Presets API 异常');
    }
    console.log('   [OK] 预设地点正常，总数:', presetsJson.presets.length);

    // 5. 测试 /api/convert
    console.log('5. 测试坐标系换算 POST /api/convert');
    const rConv = await request(`${url}/api/convert`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ lat: 30.274602, lng: 120.211963, from: 'gcj02' }),
    });
    const convJson = JSON.parse(rConv.body);
    if (!convJson.ok || !convJson.wgs84) {
      throw new Error('Convert API 异常: ' + rConv.body);
    }
    console.log('   [OK] 坐标转换正常，GCJ02->WGS84 偏移:', convJson.shiftMeters, 'm');

    // 6. 测试 /api/routes
    console.log('6. 测试路线列表 GET /api/routes');
    const rRoutes = await request(`${url}/api/routes`);
    const routesJson = JSON.parse(rRoutes.body);
    if (!routesJson.ok) {
      throw new Error('Routes API 异常: ' + rRoutes.body);
    }
    console.log('   [OK] 预设轨迹读取正常，路线数:', routesJson.routes.length);

    console.log('\n====== 全部 6 项核心测试通过！======\n');
  } finally {
    server.close();
  }
}

runTests().catch((err) => {
  console.error('测试失败:', err);
  process.exit(1);
});
