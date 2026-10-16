'use strict';

const fs = require('fs');
const path = require('path');
const { distance, interpolate } = require('./coords');

/**
 * GPX 轨迹解析与播放
 *
 * ChangeLocation 的做法是：在 Xcode 里放一个 .gpx 文件，选 Debug -> Simulate Location，
 * 由模拟器把这条轨迹回放出来。Android 没有等价入口，所以这里把 GPX 交给 ADB：
 * 解析轨迹点，按里程换算每个 tick 的位置，逐点注入。
 *
 * 不引入 XML 依赖，用正则提取 wpt / rtept / trkpt —— GPX 结构简单且固定，
 * 比引入依赖更划算。
 */

const TAGS = 'wpt|rtept|trkpt';
const POINT_RE = new RegExp(
  '<(' + TAGS + ')\\b([^>]*)>([\\s\\S]*?)<\\/(' + TAGS + ')>' +
  '|<(' + TAGS + ')\\b([^>]*)\\/>',
  'g',
);
const ATTR_RE = '\\s*=\\s*["]([^"]+)["]';

function attr(blob, name) {
  const m = blob.match(new RegExp(name + ATTR_RE, 'i'));
  return m ? m[1] : null;
}

function inner(tag, body) {
  const m = body.match(new RegExp('<' + tag + '\\b[^>]*>([^<]*)<\\/' + tag + '>', 'i'));
  return m ? m[1].trim() : null;
}

/** 解析 GPX 文本，返回轨迹点数组（GPX 原始坐标系即 WGS-84） */
function parseGpx(text) {
  const points = [];
  POINT_RE.lastIndex = 0;
  let m = POINT_RE.exec(text);
  while (m !== null) {
    const kind = m[1] || m[4];
    const blob = m[2] || m[5] || '';
    const body = m[3] || '';
    const lat = Number(attr(blob, 'lat'));
    const lon = Number(attr(blob, 'lon'));
    if (Number.isFinite(lat) && Number.isFinite(lon)) {
      const ele = inner('ele', body);
      points.push({
        kind,
        lat,
        lng: lon,
        ele: ele == null ? null : Number(ele),
        time: inner('time', body),
      });
    }
    m = POINT_RE.exec(text);
  }
  if (points.length === 0) {
    throw new Error('GPX 中没有解析到任何轨迹点（需要 wpt / rtept / trkpt）');
  }
  return points;
}

/** 读取并解析 GPX 文件 */
function load(file) {
  const abs = path.resolve(file);
  if (!fs.existsSync(abs)) {
    throw new Error('找不到 GPX 文件：' + abs);
  }
  const points = parseGpx(fs.readFileSync(abs, 'utf8'));
  return { file: abs, points };
}

/**
 * 按总时长重采样轨迹，生成固定步长的路径序列。
 * 这样无论用户给多少速度，tick 数与位置都是确定的。
 */
function resample(points, totalSeconds, stepSeconds) {
  const segs = [];
  let total = 0;
  for (let i = 1; i < points.length; i += 1) {
    const d = distance(points[i - 1], points[i]);
    segs.push(d);
    total += d;
  }

  if (points.length === 1) {
    return { path: [points[0]], totalMeters: 0, stepSeconds, totalSeconds };
  }

  const target = [];
  const count = Math.max(2, Math.ceil(totalSeconds / stepSeconds) + 1);
  for (let i = 0; i < count; i += 1) {
    const distTarget = (total * i) / (count - 1);
    let acc = 0;
    let j = 0;
    while (j < segs.length && acc + segs[j] < distTarget) {
      acc += segs[j];
      j += 1;
    }
    if (j >= segs.length) {
      target.push(points[points.length - 1]);
      continue;
    }
    const t = segs[j] === 0 ? 0 : (distTarget - acc) / segs[j];
    target.push(interpolate(points[j], points[j + 1], t));
  }

  return { path: target, totalMeters: total, stepSeconds, totalSeconds };
}

/** 统计信息：点数、总里程（米） */
function stats(points) {
  let total = 0;
  for (let i = 1; i < points.length; i += 1) total += distance(points[i - 1], points[i]);
  return { count: points.length, totalMeters: total };
}

module.exports = { parseGpx, load, resample, stats };