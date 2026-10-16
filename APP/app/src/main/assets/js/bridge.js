'use strict';

/**
 * Node 侧 JSON-RPC 服务端。
 *
 * 与桌面 CLI 共用同一份业务代码：coords.js / gpx.js / presets.js 原样 require，
 * 不做任何改动。这一点已在 Spike 1 里用真机验证过 —— 设备上的 Node
 * 直接 require 这三个文件，输出与桌面完全一致。
 *
 * 协议：stdio 每行一个 JSON 对象。
 *   请求 {"id":1,"method":"coords.convert","params":{...}}
 *   响应 {"id":1,"ok":true,"result":{...}}
 */

const path = require('path');
const fs = require('fs');

// 三个业务模块与桌面 CLI 完全相同，零改动复用
const coords = require('./coords.js');
const gpx = require('./gpx.js');
const presets = require('./presets.js');

const methods = {
  /** 握手：确认进程活着并返回版本 */
  'system.version'() {
    return { version: process.version, arch: process.arch };
  },

  /**
   * 坐标系换算。
   * params: { lat, lng, from, to }
   */
  'coords.convert'(p) {
    const out = coords.convert(Number(p.lat), Number(p.lng), p.from || 'wgs84', p.to || 'wgs84');
    return { lat: out.lat, lng: out.lng, from: p.from || 'wgs84', to: p.to || 'wgs84' };
  },

  /**
   * 把任意坐标系的点转成 WGS-84，并附带回转后的 GCJ-02 值。
   * params: { lat, lng, cs }
   */
  'coords.toWgs84'(p) {
    const cs = p.cs || 'wgs84';
    const w = coords.toWgs84(Number(p.lat), Number(p.lng), cs);
    const g = cs === 'gcj02'
      ? { lat: Number(p.lat), lng: Number(p.lng) }
      : coords.wgs84ToGcj02(w.lat, w.lng);
    return { lat: w.lat, lng: w.lng, gcj02: { lat: g.lat, lng: g.lng }, cs };
  },

  /** 两点距离（米）。params: { a:{lat,lng}, b:{lat,lng} } */
  'coords.distance'(p) {
    return { meters: coords.distance(p.a, p.b) };
  },

  /**
   * 内置地点检索。params: { keyword }
   * 返回的每条都带已转好的 WGS-84 坐标，UI 不需要再换算一次。
   */
  'presets.search'(p) {
    const list = presets.search(p.keyword || '');
    return {
      places: list.map((x) => {
        const t = presets.toPoint(x);
        return {
          name: x.name,
          cat: x.cat,
          cs: x.cs,
          aliases: x.aliases || [],
          lat: t.lat,
          lng: t.lng,
          gcj02: t.gcj02 ? { lat: t.gcj02.lat, lng: t.gcj02.lng } : null,
        };
      }),
    };
  },

  /** 精确匹配一个内置地点。params: { name } */
  'presets.resolve'(p) {
    const place = presets.resolve(p.name || '');
    if (!place) return { found: false };
    const t = presets.toPoint(place);
    return {
      found: true,
      name: place.name,
      cat: place.cat,
      cs: place.cs,
      lat: t.lat,
      lng: t.lng,
      gcj02: t.gcj02 ? { lat: t.gcj02.lat, lng: t.gcj02.lng } : null,
    };
  },

  /** 离给定坐标最近的预设点。params: { lat, lng } */
  'presets.nearest'(p) {
    const r = presets.nearest({ lat: Number(p.lat), lng: Number(p.lng) });
    if (!r) return { found: false };
    const t = presets.toPoint(r.place);
    return {
      found: true,
      name: r.place.name,
      distanceMeters: r.distance,
      lat: t.lat,
      lng: t.lng,
    };
  },

  /**
   * 解析 GPX 并按总时长重采样，一次性返回完整采样数组。
   * 之后由 Kotlin 侧原生回放，不做逐点 IPC。
   * params: { gpxPath, totalSeconds, stepSeconds }
   */
  'gpx.loadAndResample'(p) {
    const loaded = gpx.load(p.gpxPath);
    const st = gpx.stats(loaded.points);
    const cs = p.cs || 'wgs84';

    const wgsPoints = loaded.points.map((pt) => {
      const w = coords.toWgs84(pt.lat, pt.lng, cs);
      return { lat: w.lat, lng: w.lng };
    });

    const totalSeconds = Number(p.totalSeconds) || Math.max(2, Math.min(600, st.totalMeters / 5));
    const stepSeconds = Number(p.stepSeconds) || 1;

    const { path: seq } = gpx.resample(wgsPoints, totalSeconds, stepSeconds);

    return {
      file: loaded.file,
      pointCount: st.count,
      totalMeters: st.totalMeters,
      totalSeconds,
      samples: seq.map((s) => ({ lat: s.lat, lng: s.lng })),
    };
  },

  /** 只读 GPX 统计信息，不重采样。params: { gpxPath } */
  'gpx.stats'(p) {
    const loaded = gpx.load(p.gpxPath);
    const st = gpx.stats(loaded.points);
    return { file: loaded.file, pointCount: st.count, totalMeters: st.totalMeters };
  },
};

function reply(id, ok, payload) {
  const obj = { id, ok };
  if (ok) obj.result = payload;
  else obj.error = String((payload && payload.message) || payload);
  process.stdout.write(JSON.stringify(obj) + '\n');
}

let buffer = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  buffer += chunk;
  let idx = buffer.indexOf('\n');
  while (idx >= 0) {
    const line = buffer.slice(0, idx).trim();
    buffer = buffer.slice(idx + 1);
    if (line) handle(line);
    idx = buffer.indexOf('\n');
  }
});
process.stdin.on('end', () => process.exit(0));

function handle(line) {
  let req;
  try {
    req = JSON.parse(line);
  } catch (e) {
    // 无法解析的请求没有 id，只能丢弃
    process.stderr.write('bad request: ' + line + '\n');
    return;
  }

  const fn = methods[req.method];
  if (!fn) {
    reply(req.id, false, 'unknown method: ' + req.method);
    return;
  }

  try {
    const result = fn(req.params || {});
    reply(req.id, true, result);
  } catch (e) {
    process.stderr.write((e && e.stack ? e.stack : String(e)) + '\n');
    reply(req.id, false, e);
  }
}

// 告知父进程服务已就绪
process.stderr.write('locctl-bridge ready ' + process.version + '\n');
