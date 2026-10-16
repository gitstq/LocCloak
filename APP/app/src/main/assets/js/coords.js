'use strict';

/**
 * 坐标系转换（WGS-84 / GCJ-02 / BD-09）
 *
 * 取自 ChangeLocation 项目中的 gcj02ToWgs84 等方法，并做了两处改进：
 *   1. GCJ-02 -> WGS-84 使用迭代反解，误差 < 1e-9 度，替代原仓库的一次近似；
 *   2. 增加 BD-09 互转与 haversine 距离 / 方位角计算。
 */

const PI = Math.PI;
const A = 6378245.0; // 克拉索夫斯基椭球长半轴
const EE = 0.00669342162296594323; // 偏心率平方
const X_PI = (PI * 3000.0) / 180.0; // 百度坐标系偏移因子

const COORD_SYSTEMS = ['wgs84', 'gcj02', 'bd09'];

/** 是否在中国境外；境外坐标不做 GCJ-02 偏移 */
function outOfChina(lat, lng) {
  return !(lng > 73.66 && lng < 135.05 && lat > 3.86 && lat < 53.55);
}

function transformLat(x, y) {
  let ret =
    -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0;
  return ret;
}

function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0;
  return ret;
}

/** WGS-84 -> GCJ-02（高德 / 腾讯 / 微信 / 钉钉在国内使用的坐标系） */
function wgs84ToGcj02(lat, lng) {
  if (outOfChina(lat, lng)) return { lat, lng };
  let dLat = transformLat(lng - 105.0, lat - 35.0);
  let dLng = transformLng(lng - 105.0, lat - 35.0);
  const radLat = (lat / 180.0) * PI;
  let magic = Math.sin(radLat);
  magic = 1 - EE * magic * magic;
  const sqrtMagic = Math.sqrt(magic);
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI);
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * PI);
  return { lat: lat + dLat, lng: lng + dLng };
}

/** GCJ-02 -> WGS-84，迭代反解 */
function gcj02ToWgs84(lat, lng) {
  if (outOfChina(lat, lng)) return { lat, lng };
  let mLat = lat;
  let mLng = lng;
  for (let i = 0; i < 12; i += 1) {
    const f = wgs84ToGcj02(mLat, mLng);
    const dLat = f.lat - lat;
    const dLng = f.lng - lng;
    mLat -= dLat;
    mLng -= dLng;
    if (Math.abs(dLat) < 1e-9 && Math.abs(dLng) < 1e-9) break;
  }
  return { lat: mLat, lng: mLng };
}

/** GCJ-02 -> BD-09（百度地图） */
function gcj02ToBd09(lat, lng) {
  const z = Math.sqrt(lat * lat + lng * lng) + 0.00002 * Math.sin(lng * X_PI);
  const theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
  return { lat: z * Math.sin(theta) + 0.0065, lng: z * Math.cos(theta) + 0.006 };
}

/** BD-09 -> GCJ-02 */
function bd09ToGcj02(lat, lng) {
  const x = lng - 0.006;
  const y = lat - 0.0065;
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
  return { lat: z * Math.sin(theta), lng: z * Math.cos(theta) };
}

const CONVERTERS = {
  'wgs84>gcj02': wgs84ToGcj02,
  'gcj02>wgs84': gcj02ToWgs84,
  'gcj02>bd09': gcj02ToBd09,
  'bd09>gcj02': bd09ToGcj02,
  'wgs84>bd09': (lat, lng) => {
    const g = wgs84ToGcj02(lat, lng);
    return gcj02ToBd09(g.lat, g.lng);
  },
  'bd09>wgs84': (lat, lng) => {
    const g = bd09ToGcj02(lat, lng);
    return gcj02ToWgs84(g.lat, g.lng);
  },
};

/** 任意坐标系互转；from === to 时原样返回 */
function convert(lat, lng, from, to) {
  const f = String(from || 'wgs84').toLowerCase();
  const t = String(to || 'wgs84').toLowerCase();
  for (const s of [f, t]) {
    if (!COORD_SYSTEMS.includes(s)) {
      throw new Error(`未知坐标系 "${s}"，可选：${COORD_SYSTEMS.join(' / ')}`);
    }
  }
  if (f === t) return { lat, lng };
  const fn = CONVERTERS[`${f}>${t}`];
  return fn(lat, lng);
}

/** 系统底层定位（gps provider）只认 WGS-84，注入前统一转换 */
function toWgs84(lat, lng, from) {
  return convert(lat, lng, from || 'wgs84', 'wgs84');
}

/** 两点球面距离（米） */
function distance(a, b) {
  const R = 6371008.8;
  const dLat = ((b.lat - a.lat) * PI) / 180;
  const dLng = ((b.lng - a.lng) * PI) / 180;
  const la1 = (a.lat * PI) / 180;
  const la2 = (b.lat * PI) / 180;
  const h =
    Math.sin(dLat / 2) ** 2 + Math.sin(dLng / 2) ** 2 * Math.cos(la1) * Math.cos(la2);
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** 从 a 指向 b 的方位角（度，0 = 正北） */
function bearing(a, b) {
  const la1 = (a.lat * PI) / 180;
  const la2 = (b.lat * PI) / 180;
  const dLng = ((b.lng - a.lng) * PI) / 180;
  const y = Math.sin(dLng) * Math.cos(la2);
  const x = Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dLng);
  return (Math.atan2(y, x) * 180 / PI + 360) % 360;
}

/** 解析 "lat,lng" / "lat,lng,cs" / "lat lng" */
function parsePoint(input, defaultCs = 'wgs84') {
  if (input == null) throw new Error('缺少坐标');
  if (typeof input === 'object' && typeof input.lat === 'number') return input;

  const parts = String(input)
    .trim()
    .split(/[,;\s]+/)
    .filter(Boolean);
  if (parts.length < 2) throw new Error(`无法解析坐标："${input}"，期望格式 lat,lng`);

  const lat = Number(parts[0]);
  const lng = Number(parts[1]);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    throw new Error(`坐标必须是数字："${input}"`);
  }
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    throw new Error(`坐标超出范围：lat∈[-90,90] lng∈[-180,180]，收到 ${lat},${lng}`);
  }
  const cs = (parts[2] || defaultCs).toLowerCase();
  return { lat, lng, cs };
}

/** 线性插值（轨迹平滑用） */
function interpolate(a, b, t) {
  return { lat: a.lat + (b.lat - a.lat) * t, lng: a.lng + (b.lng - a.lng) * t };
}

module.exports = {
  COORD_SYSTEMS,
  convert,
  toWgs84,
  wgs84ToGcj02,
  gcj02ToWgs84,
  gcj02ToBd09,
  bd09ToGcj02,
  outOfChina,
  distance,
  bearing,
  interpolate,
  parsePoint,
};