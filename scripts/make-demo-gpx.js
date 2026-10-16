'use strict';

// 一次性脚本：由 GCJ-02 途经点生成 WGS-84 的 GPX 轨迹示例
const fs = require('fs');
const path = require('path');
const c = require('../src/coords');

const WAYPOINTS = [
  [30.259, 120.1487, '断桥残雪'],
  [30.256, 120.142, '平湖秋月'],
  [30.249, 120.133, '曲院风荷'],
  [30.238, 120.139, '苏堤春晓'],
  [30.233, 120.14, '花港观鱼'],
  [30.231, 120.1487, '雷峰塔'],
  [30.243, 120.155, '柳浪闻莺'],
  [30.252, 120.162, '湖滨公园'],
  [30.259, 120.1487, '断桥残雪（终点）'],
];

const SEGMENTS = 20;
const out = [];
let count = 0;

for (let i = 1; i < WAYPOINTS.length; i += 1) {
  const a = WAYPOINTS[i - 1];
  const b = WAYPOINTS[i];
  for (let s = 0; s < SEGMENTS; s += 1) {
    const t = s / SEGMENTS;
    const gcjLat = a[0] + (b[0] - a[0]) * t;
    const gcjLng = a[1] + (b[1] - a[1]) * t;
    // GCJ-02 -> WGS-84：GPX 格式要求 WGS-84
    const w = c.gcj02ToWgs84(gcjLat, gcjLng);
    count += 1;
    out.push(`   <trkpt lat="${w.lat.toFixed(7)}" lon="${w.lng.toFixed(7)}"><ele>12</ele></trkpt>`);
  }
}

const doc = [
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<gpx version="1.1" creator="locctl" xmlns="http://www.topografix.com/GPX/1/1">',
  '  <metadata>',
  '    <name>西湖环湖徒步</name>',
  '    <desc>断桥 - 白堤 - 苏堤 - 雷峰塔 - 柳浪闻莺 - 湖滨公园 - 断桥</desc>',
  '  </metadata>',
  '  <trk>',
  '    <name>西湖环湖徒步</name>',
  '    <trkseg>',
  ...out,
  '    </trkseg>',
  '  </trk>',
  '</gpx>',
  '',
].join('\n');

const target = path.join(__dirname, '..', 'routes', 'west-lake-loop.gpx');
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, doc, 'utf8');

const gpx = require('../src/gpx');
const parsed = gpx.load(target);
const st = gpx.stats(parsed.points);
console.log(`已生成 ${target}`);
console.log(`  写入点 ${count}，解析点 ${st.count}，总里程 ${(st.totalMeters / 1000).toFixed(2)} km`);