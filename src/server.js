'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { ADB_BIN, adb, adbText, adbTry, devices, deviceInfo } = require('./adb');
const mock = require('./mock');
const coords = require('./coords');
const presets = require('./presets');
const gpx = require('./gpx');

const WEB_DIR = path.join(__dirname, 'web');
const ROUTES_DIR = path.join(__dirname, '..', 'routes');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.gpx': 'application/gpx+xml; charset=utf-8',
};

// ------------------------------------------------------------- 状态与事件管理
const sseClients = new Set();
const logHistory = [];
const MAX_LOGS = 300;

function addLog(level, message, detail = null) {
  const item = {
    id: Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    timestamp: Date.now(),
    level, // 'info' | 'success' | 'warn' | 'error'
    message,
    detail,
  };
  logHistory.push(item);
  if (logHistory.length > MAX_LOGS) logHistory.shift();

  const payload = `data: ${JSON.stringify({ type: 'log', data: item })}\n\n`;
  for (const client of sseClients) {
    try {
      client.write(payload);
    } catch {
      sseClients.delete(client);
    }
  }
}

function broadcastTaskState() {
  const data = getTaskStatus();
  const payload = `data: ${JSON.stringify({ type: 'task', data })}\n\n`;
  for (const client of sseClients) {
    try {
      client.write(payload);
    } catch {
      sseClients.delete(client);
    }
  }
}

// ------------------------------------------------------------- 任务调度器 (TaskManager)
let activeTask = {
  mode: 'idle', // 'idle' | 'hold' | 'wander' | 'path'
  startTime: null,
  pushes: 0,
  errors: 0,
  currentPoint: null, // { lat, lng, wgs84: {lat, lng}, gcj02: {lat, lng} }
  target: null,
  interval: 500,
  accuracy: 5,
  providers: [...mock.DEFAULT_PROVIDERS],
  pusher: null,
  timer: null,
  pathProgress: null, // { currentIdx, totalPoints, totalMeters, travelledMeters, speedKmh }
};

function getTaskStatus() {
  return {
    mode: activeTask.mode,
    startTime: activeTask.startTime,
    durationSeconds: activeTask.startTime ? Math.round((Date.now() - activeTask.startTime) / 1000) : 0,
    pushes: activeTask.pushes,
    errors: activeTask.errors,
    currentPoint: activeTask.currentPoint,
    target: activeTask.target,
    interval: activeTask.interval,
    accuracy: activeTask.accuracy,
    providers: activeTask.providers,
    pathProgress: activeTask.pathProgress,
  };
}

function stopCurrentTask({ quiet = false } = {}) {
  if (activeTask.timer) {
    clearInterval(activeTask.timer);
    clearTimeout(activeTask.timer);
    activeTask.timer = null;
  }
  if (activeTask.pusher) {
    activeTask.pusher.stop();
    activeTask.pusher = null;
  }
  const prevMode = activeTask.mode;
  const pushes = activeTask.pushes;
  activeTask.mode = 'idle';
  activeTask.startTime = null;
  activeTask.pathProgress = null;

  if (prevMode !== 'idle' && !quiet) {
    addLog('info', `已停止【${prevMode}】任务（共推送 ${pushes} 次）`);
  }
  broadcastTaskState();
}

async function ensureMockedProviders(providers) {
  const st = await mock.status();
  const missing = providers.filter((p) => !st.mocked[p]);
  if (missing.length > 0) {
    addLog('info', `正在接管 Mock Provider：${missing.join(', ')}...`);
    await mock.install(providers, { quiet: true });
    addLog('success', `Provider 接管成功：${missing.join(', ')}`);
  }
  return true;
}

// ------------------------------------------------------------- HTTP 请求解析辅助
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => {
      data += chunk;
      if (data.length > 20 * 1024 * 1024) {
        reject(new Error('Payload too large'));
      }
    });
    req.on('end', () => {
      if (!data) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch (err) {
        resolve({ raw: data });
      }
    });
    req.on('error', reject);
  });
}

function jsonResponse(res, data, status = 200) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store, no-cache, must-revalidate',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  });
  res.end(JSON.stringify(data));
}

function errorResponse(res, message, status = 400) {
  jsonResponse(res, { ok: false, error: String(message) }, status);
}

// ------------------------------------------------------------- 核心业务路由处理
async function handleApi(req, res, pathname) {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    });
    return res.end();
  }

  // 1. 获取全局综合状态 (ADB、设备、Provider、定位开关、任务状态)
  if (pathname === '/api/status' && req.method === 'GET') {
    try {
      const devList = await devices();
      const onlineDevs = devList.filter((d) => d.state === 'device');
      const curSerial = process.env.LOCCTL_SERIAL || (onlineDevs[0] ? onlineDevs[0].id : '');

      let currentDevice = null;
      let mockStatus = { mocked: {}, last: {}, locationEnabled: null };
      let permissionGranted = false;

      if (onlineDevs.length > 0) {
        try {
          currentDevice = await deviceInfo();
        } catch {
          currentDevice = onlineDevs[0];
        }
        try {
          mockStatus = await mock.status();
        } catch {}
        try {
          const op = await adbTry([
            'shell',
            'appops',
            'get',
            'com.android.shell',
            'android:mock_location',
          ]);
          permissionGranted = /:\s*allow/i.test(op.out);
        } catch {}
      }

      const isBuiltin = ADB_BIN.toLowerCase().includes('platform-tools') || ADB_BIN.toLowerCase().includes('bin\\');

      return jsonResponse(res, {
        ok: true,
        adb: {
          path: ADB_BIN,
          builtin: isBuiltin,
        },
        serial: curSerial,
        devices: devList,
        deviceInfo: currentDevice,
        mockStatus,
        permissionGranted,
        task: getTaskStatus(),
      });
    } catch (err) {
      return jsonResponse(res, {
        ok: false,
        error: err.message,
        adb: { path: ADB_BIN },
        task: getTaskStatus(),
      });
    }
  }

  // 2. 切换当前操作的设备
  if (pathname === '/api/device/select' && req.method === 'POST') {
    const body = await readBody(req);
    const targetSerial = body.serial || '';
    process.env.LOCCTL_SERIAL = targetSerial;
    addLog('info', `已切换目标设备: ${targetSerial || '默认首选设备'}`);
    return jsonResponse(res, { ok: true, serial: targetSerial });
  }

  // 3. 执行 Doctor 环境自检与自动修复
  if (pathname === '/api/doctor' && req.method === 'POST') {
    const body = await readBody(req);
    const fix = body.fix !== false;
    try {
      addLog('info', `正在执行环境诊断${fix ? '并自动修复权限' : ''}...`);
      const r = await mock.doctor({ fix, quiet: true });
      addLog('success', `环境诊断完成，已连接设备: ${r.info.model} (Android ${r.info.release})`);
      if (r.notes && r.notes.length) {
        for (const n of r.notes) addLog('warn', `诊断提示: ${n}`);
      }
      return jsonResponse(res, { ok: true, result: r });
    } catch (err) {
      addLog('error', `环境诊断失败: ${err.message}`);
      return errorResponse(res, err.message);
    }
  }

  // 4. 定位总开关控制
  if (pathname === '/api/location/enable' && req.method === 'POST') {
    const body = await readBody(req);
    const enabled = Boolean(body.enabled);
    try {
      await adb(['shell', 'cmd', 'location', 'set-location-enabled', enabled ? 'true' : 'false']);
      addLog('success', `系统定位总开关已设置为: ${enabled ? '开启' : '关闭'}`);
      return jsonResponse(res, { ok: true, enabled });
    } catch (err) {
      addLog('error', `设置定位总开关失败: ${err.message}`);
      return errorResponse(res, err.message);
    }
  }

  // 5. 预设地点列表
  if (pathname === '/api/presets' && req.method === 'GET') {
    const all = presets.all();
    const categories = presets.categories();
    const list = all.map((p) => {
      const pt = presets.toPoint(p);
      return {
        name: p.name,
        cat: p.cat,
        cs: p.cs,
        lat: p.lat,
        lng: p.lng,
        wgs84: { lat: pt.lat, lng: pt.lng },
        gcj02: pt.gcj02 ? { lat: pt.gcj02.lat, lng: pt.gcj02.lng } : null,
      };
    });
    return jsonResponse(res, { ok: true, presets: list, categories });
  }

  // 6. 坐标系换算工具
  if (pathname === '/api/convert' && req.method === 'POST') {
    const body = await readBody(req);
    const lat = Number(body.lat);
    const lng = Number(body.lng);
    const from = body.from || 'gcj02';
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return errorResponse(res, '请输入合法的经纬度数字');
    }
    const wgs84 = coords.convert(lat, lng, from, 'wgs84');
    const gcj02 = coords.convert(lat, lng, from, 'gcj02');
    const bd09 = coords.convert(lat, lng, from, 'bd09');
    const shift = coords.distance({ lat, lng }, wgs84);
    return jsonResponse(res, {
      ok: true,
      input: { lat, lng, cs: from },
      wgs84,
      gcj02,
      bd09,
      shiftMeters: Math.round(shift * 10) / 10,
    });
  }

  // 7. 单次瞬移注入 (Goto / Set)
  if (pathname === '/api/inject/goto' && req.method === 'POST') {
    const body = await readBody(req);
    const lat = Number(body.lat);
    const lng = Number(body.lng);
    const cs = body.cs || 'gcj02';
    const accuracy = Number(body.accuracy) || 5;
    const providers = Array.isArray(body.providers) && body.providers.length > 0 ? body.providers : mock.DEFAULT_PROVIDERS;

    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return errorResponse(res, '坐标无效');
    }

    try {
      stopCurrentTask({ quiet: true });
      await ensureMockedProviders(providers);

      const target = coords.toWgs84(lat, lng, cs);
      await mock.pushOnce(target, { providers, accuracy });

      activeTask.currentPoint = {
        lat: target.lat,
        lng: target.lng,
        wgs84: { lat: target.lat, lng: target.lng },
        gcj02: target.gcj02 || coords.convert(target.lat, target.lng, 'wgs84', 'gcj02'),
      };
      activeTask.target = body.name || `${lat.toFixed(6)}, ${lng.toFixed(6)}`;

      addLog('success', `单次注入成功 -> ${activeTask.target} (WGS-84: ${target.lat.toFixed(6)}, ${target.lng.toFixed(6)})`);
      broadcastTaskState();
      return jsonResponse(res, { ok: true, point: activeTask.currentPoint });
    } catch (err) {
      addLog('error', `单次注入失败: ${err.message}`);
      return errorResponse(res, err.message);
    }
  }

  // 8. 常驻循环推送 (Hold) - 核心模式，防微信失效
  if (pathname === '/api/inject/hold' && req.method === 'POST') {
    const body = await readBody(req);
    const lat = Number(body.lat);
    const lng = Number(body.lng);
    const cs = body.cs || 'gcj02';
    const interval = Math.max(100, Number(body.interval) || 500);
    const accuracy = Math.max(1, Number(body.accuracy) || 5);
    const providers = Array.isArray(body.providers) && body.providers.length > 0 ? body.providers : mock.DEFAULT_PROVIDERS;

    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return errorResponse(res, '坐标无效');
    }

    try {
      stopCurrentTask({ quiet: true });
      await ensureMockedProviders(providers);

      const target = coords.toWgs84(lat, lng, cs);
      const pusher = new mock.Pusher(providers);
      pusher.start(target, { interval, accuracy });

      activeTask = {
        mode: 'hold',
        startTime: Date.now(),
        pushes: 0,
        errors: 0,
        currentPoint: {
          lat: target.lat,
          lng: target.lng,
          wgs84: { lat: target.lat, lng: target.lng },
          gcj02: target.gcj02 || coords.convert(target.lat, target.lng, 'wgs84', 'gcj02'),
        },
        target: body.name || `${lat.toFixed(6)}, ${lng.toFixed(6)}`,
        interval,
        accuracy,
        providers,
        pusher,
        timer: null,
        pathProgress: null,
      };

      // 状态定时更新同步
      activeTask.timer = setInterval(() => {
        if (activeTask.mode !== 'hold' || !activeTask.pusher) return;
        activeTask.pushes = activeTask.pusher.pushes;
        activeTask.errors = activeTask.pusher.errors;
        broadcastTaskState();
      }, 1000);

      addLog('success', `已启动常驻推送【${activeTask.target}】，间隔 ${interval}ms，精度 ${accuracy}m`);
      broadcastTaskState();
      return jsonResponse(res, { ok: true, task: getTaskStatus() });
    } catch (err) {
      addLog('error', `启动常驻推送失败: ${err.message}`);
      return errorResponse(res, err.message);
    }
  }

  // 9. 随机漫游 (Wander) - 模拟散步走动防风控
  if (pathname === '/api/inject/wander' && req.method === 'POST') {
    const body = await readBody(req);
    const lat = Number(body.lat);
    const lng = Number(body.lng);
    const cs = body.cs || 'gcj02';
    const radius = Math.max(10, Number(body.radius) || 200);
    const interval = Math.max(500, Number(body.interval) || 2000);
    const accuracy = Math.max(1, Number(body.accuracy) || 10);
    const providers = Array.isArray(body.providers) && body.providers.length > 0 ? body.providers : mock.DEFAULT_PROVIDERS;

    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return errorResponse(res, '坐标无效');
    }

    try {
      stopCurrentTask({ quiet: true });
      await ensureMockedProviders(providers);

      const center = coords.toWgs84(lat, lng, cs);
      const mPerDegLat = 111320;
      const mPerDegLng = 111320 * Math.cos((center.lat * Math.PI) / 180);

      const pusher = new mock.Pusher(providers);
      pusher.start(center, {
        interval,
        accuracy,
        onTick: (cur) => {
          const r = radius * Math.sqrt(Math.random());
          const th = Math.random() * Math.PI * 2;
          const nextWgs = {
            lat: center.lat + (r * Math.sin(th)) / mPerDegLat,
            lng: center.lng + (r * Math.cos(th)) / mPerDegLng,
          };
          activeTask.currentPoint = {
            lat: nextWgs.lat,
            lng: nextWgs.lng,
            wgs84: { lat: nextWgs.lat, lng: nextWgs.lng },
            gcj02: coords.convert(nextWgs.lat, nextWgs.lng, 'wgs84', 'gcj02'),
          };
          return nextWgs;
        },
      });

      activeTask = {
        mode: 'wander',
        startTime: Date.now(),
        pushes: 0,
        errors: 0,
        currentPoint: {
          lat: center.lat,
          lng: center.lng,
          wgs84: { lat: center.lat, lng: center.lng },
          gcj02: center.gcj02 || coords.convert(center.lat, center.lng, 'wgs84', 'gcj02'),
        },
        target: `以 ${body.name || `${lat.toFixed(5)},${lng.toFixed(5)}`} 为中心 ${radius}m 范围`,
        interval,
        accuracy,
        providers,
        pusher,
        timer: null,
        pathProgress: null,
      };

      activeTask.timer = setInterval(() => {
        if (activeTask.mode !== 'wander' || !activeTask.pusher) return;
        activeTask.pushes = activeTask.pusher.pushes;
        activeTask.errors = activeTask.pusher.errors;
        broadcastTaskState();
      }, 1000);

      addLog('success', `已启动随机漫游：${activeTask.target}，步频 ${interval}ms`);
      broadcastTaskState();
      return jsonResponse(res, { ok: true, task: getTaskStatus() });
    } catch (err) {
      addLog('error', `启动漫游失败: ${err.message}`);
      return errorResponse(res, err.message);
    }
  }

  // 10. GPX 轨迹回放 (Path)
  if (pathname === '/api/inject/path' && req.method === 'POST') {
    const body = await readBody(req);
    const speedKmh = Math.max(1, Number(body.speed) || 15);
    const interval = Math.max(100, Number(body.interval) || 1000);
    const accuracy = Math.max(1, Number(body.accuracy) || 5);
    const providers = Array.isArray(body.providers) && body.providers.length > 0 ? body.providers : mock.DEFAULT_PROVIDERS;
    let points = [];

    if (body.gpxContent) {
      try {
        const parsed = gpx.parse(body.gpxContent);
        points = parsed.points;
      } catch (e) {
        return errorResponse(res, `解析 GPX 内容失败: ${e.message}`);
      }
    } else if (body.routeName) {
      const fullPath = path.join(ROUTES_DIR, body.routeName);
      if (!fs.existsSync(fullPath)) {
        return errorResponse(res, `找不到轨迹文件: ${body.routeName}`);
      }
      points = gpx.load(fullPath).points;
    } else {
      return errorResponse(res, '请提供 GPX 轨迹或选择预设路线');
    }

    if (points.length < 2) {
      return errorResponse(res, 'GPX 轨迹点数过少（至少需要2个点）');
    }

    try {
      stopCurrentTask({ quiet: true });
      await ensureMockedProviders(providers);

      const st = gpx.stats(points);
      const totalSeconds = Math.max(2, (st.totalMeters / 1000 / speedKmh) * 3600);
      const cs = body.cs || 'wgs84';
      const wgsPoints = points.map((p) => toWgs84Point(p, cs));

      const { path: seq } = gpx.resample(wgsPoints, totalSeconds, interval / 1000);
      const stepMs = Math.max(50, (seq.length > 1 ? totalSeconds / (seq.length - 1) : 1) * 1000);

      const pusher = new mock.Pusher(providers);
      await pusher.push(seq[0]);

      let curIdx = 0;
      activeTask = {
        mode: 'path',
        startTime: Date.now(),
        pushes: 1,
        errors: 0,
        currentPoint: {
          lat: seq[0].lat,
          lng: seq[0].lng,
          wgs84: { lat: seq[0].lat, lng: seq[0].lng },
          gcj02: coords.convert(seq[0].lat, seq[0].lng, 'wgs84', 'gcj02'),
        },
        target: body.routeName || '自定义 GPX 轨迹',
        interval: Math.round(stepMs),
        accuracy,
        providers,
        pusher,
        timer: null,
        pathProgress: {
          currentIdx: 0,
          totalPoints: seq.length,
          totalMeters: Math.round(st.totalMeters),
          travelledMeters: 0,
          speedKmh,
          totalSeconds: Math.round(totalSeconds),
        },
      };

      activeTask.timer = setInterval(async () => {
        curIdx += 1;
        if (curIdx >= seq.length) {
          clearInterval(activeTask.timer);
          activeTask.timer = null;
          pusher.stop();
          activeTask.mode = 'idle';
          addLog('success', `轨迹回放完成！总里程 ${coords.fmtDistance ? coords.fmtDistance(st.totalMeters) : Math.round(st.totalMeters) + 'm'}`);
          broadcastTaskState();
          return;
        }

        try {
          const pt = seq[curIdx];
          await pusher.push(pt);
          activeTask.pushes += 1;
          activeTask.currentPoint = {
            lat: pt.lat,
            lng: pt.lng,
            wgs84: { lat: pt.lat, lng: pt.lng },
            gcj02: coords.convert(pt.lat, pt.lng, 'wgs84', 'gcj02'),
          };
          activeTask.pathProgress.currentIdx = curIdx;
          activeTask.pathProgress.travelledMeters = Math.round(coords.distance(seq[0], pt));
          broadcastTaskState();
        } catch (err) {
          clearInterval(activeTask.timer);
          activeTask.timer = null;
          pusher.stop();
          activeTask.mode = 'idle';
          addLog('error', `轨迹回放中断: ${err.message}`);
          broadcastTaskState();
        }
      }, stepMs);

      addLog('success', `开始回放轨迹【${activeTask.target}】，速度 ${speedKmh}km/h，共 ${seq.length} 个采样点，预计耗时 ${Math.round(totalSeconds)}s`);
      broadcastTaskState();
      return jsonResponse(res, { ok: true, task: getTaskStatus() });
    } catch (err) {
      addLog('error', `启动轨迹失败: ${err.message}`);
      return errorResponse(res, err.message);
    }
  }

  // 11. 停止注入并恢复真实定位 (Stop)
  if (pathname === '/api/inject/stop' && req.method === 'POST') {
    try {
      stopCurrentTask({ quiet: false });
      addLog('info', '正在卸载 mock providers 并恢复真实定位...');
      await mock.uninstall(mock.DEFAULT_PROVIDERS, { quiet: true });
      addLog('success', '已还原为真实系统定位！');
      broadcastTaskState();
      return jsonResponse(res, { ok: true });
    } catch (err) {
      addLog('error', `还原定位失败: ${err.message}`);
      return errorResponse(res, err.message);
    }
  }

  // 12. 扫描预设轨迹列表
  if (pathname === '/api/routes' && req.method === 'GET') {
    try {
      if (!fs.existsSync(ROUTES_DIR)) {
        return jsonResponse(res, { ok: true, routes: [] });
      }
      const files = fs.readdirSync(ROUTES_DIR).filter((f) => f.endsWith('.gpx'));
      const list = [];
      for (const f of files) {
        try {
          const { points } = gpx.load(path.join(ROUTES_DIR, f));
          const st = gpx.stats(points);
          list.push({
            filename: f,
            pointsCount: st.count,
            totalMeters: Math.round(st.totalMeters),
            bounds: st.bounds,
            previewPoints: points.slice(0, 50).map((p) => ({ lat: p.lat, lng: p.lng })),
          });
        } catch {
          list.push({ filename: f, pointsCount: 0, totalMeters: 0 });
        }
      }
      return jsonResponse(res, { ok: true, routes: list });
    } catch (err) {
      return errorResponse(res, err.message);
    }
  }

  // 13. 解析上传的 GPX 文件内容
  if (pathname === '/api/routes/parse' && req.method === 'POST') {
    const body = await readBody(req);
    if (!body.content) return errorResponse(res, '缺少 GPX 内容');
    try {
      const { points } = gpx.parse(body.content);
      const st = gpx.stats(points);
      return jsonResponse(res, {
        ok: true,
        stats: {
          count: st.count,
          totalMeters: Math.round(st.totalMeters),
          bounds: st.bounds,
        },
        points: points.map((p) => ({ lat: p.lat, lng: p.lng })),
      });
    } catch (err) {
      return errorResponse(res, `解析失败: ${err.message}`);
    }
  }

  // 14. SSE 实时事件日志流
  if (pathname === '/api/logs/stream' && req.method === 'GET') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'Access-Control-Allow-Origin': '*',
    });
    res.write(': connected\n\n');

    // 首次连接发送近期历史日志
    res.write(`data: ${JSON.stringify({ type: 'history', data: logHistory })}\n\n`);
    res.write(`data: ${JSON.stringify({ type: 'task', data: getTaskStatus() })}\n\n`);

    sseClients.add(res);
    req.on('close', () => {
      sseClients.delete(res);
    });
    return;
  }

  return null; // 未匹配 API
}

function toWgs84Point(p, cs) {
  const w = coords.toWgs84(p.lat, p.lng, cs);
  return { ...w, raw: p };
}

// ------------------------------------------------------------- 静态文件服务
function serveStatic(req, res, pathname) {
  let relativePath = pathname === '/' ? 'index.html' : pathname.replace(/^\//, '');
  // 安全限制：禁止目录遍历
  const safePath = path.normalize(relativePath).replace(/^(\.\.[\/\\])+/, '');
  const filePath = path.join(WEB_DIR, safePath);

  if (!filePath.startsWith(WEB_DIR)) {
    res.writeHead(403);
    return res.end('Forbidden');
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      // 找不到则回退到 index.html
      const fallbackPath = path.join(WEB_DIR, 'index.html');
      fs.readFile(fallbackPath, (err2, content) => {
        if (err2) {
          res.writeHead(404);
          return res.end('Not Found');
        }
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(content);
      });
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    res.writeHead(200, {
      'Content-Type': contentType,
      'Content-Length': stats.size,
      'Cache-Control': 'no-cache',
    });
    fs.createReadStream(filePath).pipe(res);
  });
}

// ------------------------------------------------------------- 创建并启动服务
function createServer() {
  const server = http.createServer(async (req, res) => {
    try {
      const parsedUrl = new URL(req.url, 'http://localhost');
      const pathname = parsedUrl.pathname;

      if (pathname.startsWith('/api/')) {
        const handled = await handleApi(req, res, pathname);
        if (handled !== null) return;
        return errorResponse(res, 'API not found', 404);
      }

      serveStatic(req, res, pathname);
    } catch (err) {
      console.error('Server error:', err);
      if (!res.headersSent) {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Internal Server Error: ' + err.message);
      }
    }
  });

  return server;
}

// ------------------------------------------------------------- 启动 UI 桌面模式/浏览器
function openInDesktop(url) {
  const edgePaths = [
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    path.join(process.env.LOCALAPPDATA || '', 'Microsoft\\Edge\\Application\\msedge.exe'),
  ];

  let edgeExe = edgePaths.find((p) => fs.existsSync(p));

  if (process.platform === 'win32' && edgeExe) {
    // Edge App 窗口模式：像真正的独立原生软件一样展示
    const child = spawn(edgeExe, [`--app=${url}`, '--window-size=1260,860', '--no-first-run'], {
      detached: true,
      stdio: 'ignore',
    });
    child.unref();
    return true;
  }

  // 跨平台回退到默认浏览器
  const startCmd =
    process.platform === 'win32'
      ? ['cmd.exe', ['/c', 'start', '""', url]]
      : process.platform === 'darwin'
      ? ['open', [url]]
      : ['xdg-open', [url]];

  try {
    const child = spawn(startCmd[0], startCmd[1], { detached: true, stdio: 'ignore' });
    child.unref();
    return true;
  } catch {
    return false;
  }
}

function startServer({ port = 8765, open = true } = {}) {
  return new Promise((resolve, reject) => {
    const server = createServer();

    server.on('error', (err) => {
      if (err.code === 'EADDRINUSE') {
        // 端口被占用，尝试下一个端口
        console.log(`端口 ${port} 被占用，尝试端口 ${port + 1}...`);
        startServer({ port: port + 1, open }).then(resolve).catch(reject);
      } else {
        reject(err);
      }
    });

    server.listen(port, '127.0.0.1', () => {
      const url = `http://127.0.0.1:${port}`;
      addLog('info', `locctl UI 控制台服务已启动: ${url}`);
      addLog('info', `使用 ADB: ${ADB_BIN}`);

      if (open) {
        setTimeout(() => {
          openInDesktop(url);
        }, 300);
      }

      resolve({ server, port, url });
    });
  });
}

module.exports = {
  startServer,
  createServer,
  openInDesktop,
  stopCurrentTask,
  getTaskStatus,
  addLog,
};
