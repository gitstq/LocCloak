'use strict';

/**
 * 内置地点库
 *
 * 取自 VirtualLocation 的「选择城市」交互：无需联网、无需地图 Key 即可直接跳转。
 * 坐标说明：国内条目统一存 GCJ-02（高德坐标系），注入前由 coords.toWgs84 转成
 * WGS-84；境外条目直接存 WGS-84（境外不做 GCJ 偏移）。
 */

const { toWgs84, distance } = require('./coords');

const PLACES = [
  // ---- 国内（GCJ-02）----
  { name: '北京天安门', lat: 39.90923, lng: 116.397428, cs: 'gcj02', cat: '地标', aliases: ['天安门', '北京'] },
  { name: '北京故宫', lat: 39.918058, lng: 116.397026, cs: 'gcj02', cat: '地标', aliases: ['故宫'] },
  { name: '北京中关村', lat: 39.98295, lng: 116.31607, cs: 'gcj02', cat: '地标', aliases: ['中关村'] },
  { name: '上海外滩', lat: 31.241912, lng: 121.490316, cs: 'gcj02', cat: '地标', aliases: ['外滩', '上海'] },
  { name: '上海人民广场', lat: 31.230416, lng: 121.473701, cs: 'gcj02', cat: '地标', aliases: ['人民广场'] },
  { name: '上海陆家嘴', lat: 31.237437, lng: 121.50101, cs: 'gcj02', cat: '地标', aliases: ['陆家嘴'] },
  { name: '广州塔', lat: 23.099994, lng: 113.32452, cs: 'gcj02', cat: '地标', aliases: ['小蛮腰', '广州'] },
  { name: '深圳平安金融中心', lat: 22.548515, lng: 114.066112, cs: 'gcj02', cat: '地标', aliases: ['平安金融中心', '深圳'] },
  { name: '杭州西湖', lat: 30.241927, lng: 120.146295, cs: 'gcj02', cat: '地标', aliases: ['西湖', '杭州'] },
  { name: '南京夫子庙', lat: 32.050233, lng: 118.786996, cs: 'gcj02', cat: '地标', aliases: ['夫子庙', '南京'] },
  { name: '成都春熙路', lat: 30.657081, lng: 104.081386, cs: 'gcj02', cat: '地标', aliases: ['春熙路', '成都'] },
  { name: '重庆解放碑', lat: 29.558285, lng: 106.577232, cs: 'gcj02', cat: '地标', aliases: ['解放碑', '重庆'] },
  { name: '武汉黄鹤楼', lat: 30.545977, lng: 114.302849, cs: 'gcj02', cat: '地标', aliases: ['黄鹤楼', '武汉'] },
  { name: '西安钟楼', lat: 34.261161, lng: 108.942866, cs: 'gcj02', cat: '地标', aliases: ['钟楼', '西安'] },
  { name: '天津滨江道', lat: 39.124219, lng: 117.200843, cs: 'gcj02', cat: '城市', aliases: ['天津'] },
  { name: '苏州观前街', lat: 31.310537, lng: 120.617427, cs: 'gcj02', cat: '城市', aliases: ['苏州'] },
  { name: '长沙橘子洲', lat: 28.189312, lng: 112.963613, cs: 'gcj02', cat: '地标', aliases: ['橘子洲', '长沙'] },
  { name: '青岛栈桥', lat: 36.057537, lng: 120.311951, cs: 'gcj02', cat: '地标', aliases: ['栈桥', '青岛'] },
  { name: '厦门鼓浪屿', lat: 24.446701, lng: 118.067663, cs: 'gcj02', cat: '地标', aliases: ['鼓浪屿', '厦门'] },
  { name: '昆明翠湖', lat: 24.889193, lng: 102.706271, cs: 'gcj02', cat: '城市', aliases: ['翠湖', '昆明'] },
  { name: '贵阳甲秀楼', lat: 26.567018, lng: 106.709194, cs: 'gcj02', cat: '地标', aliases: ['甲秀楼', '贵阳'] },
  { name: '哈尔滨中央大街', lat: 45.771362, lng: 126.617837, cs: 'gcj02', cat: '地标', aliases: ['中央大街', '哈尔滨'] },
  { name: '沈阳中街', lat: 41.796767, lng: 123.457237, cs: 'gcj02', cat: '地标', aliases: ['沈阳'] },
  { name: '郑州二七广场', lat: 34.753088, lng: 113.665087, cs: 'gcj02', cat: '地标', aliases: ['二七广场', '郑州'] },
  { name: '济南泉城广场', lat: 36.663206, lng: 117.024284, cs: 'gcj02', cat: '地标', aliases: ['泉城广场', '济南'] },
  { name: '合肥步行街', lat: 31.861546, lng: 117.283111, cs: 'gcj02', cat: '城市', aliases: ['合肥'] },
  { name: '福州三坊七巷', lat: 26.074202, lng: 119.296337, cs: 'gcj02', cat: '地标', aliases: ['三坊七巷', '福州'] },
  { name: '南昌滕王阁', lat: 28.682951, lng: 115.881613, cs: 'gcj02', cat: '地标', aliases: ['滕王阁', '南昌'] },
  { name: '太原钟楼', lat: 37.857014, lng: 112.549248, cs: 'gcj02', cat: '地标', aliases: ['太原'] },
  { name: '石家庄中山路', lat: 38.045474, lng: 114.502461, cs: 'gcj02', cat: '城市', aliases: ['石家庄'] },
  { name: '南宁青秀山', lat: 22.785678, lng: 108.398277, cs: 'gcj02', cat: '地标', aliases: ['青秀山', '南宁'] },
  { name: '兰州中山桥', lat: 36.058039, lng: 103.823557, cs: 'gcj02', cat: '地标', aliases: ['中山桥', '兰州'] },
  { name: '乌鲁木齐红山', lat: 43.825592, lng: 87.616848, cs: 'gcj02', cat: '地标', aliases: ['红山', '乌鲁木齐'] },
  { name: '拉萨布达拉宫', lat: 29.657921, lng: 91.117212, cs: 'gcj02', cat: '地标', aliases: ['布达拉宫', '拉萨'] },
  { name: '海口骑楼老街', lat: 20.021532, lng: 110.335561, cs: 'gcj02', cat: '地标', aliases: ['骑楼老街', '海口'] },
  { name: '三亚亚龙湾', lat: 18.221189, lng: 109.23177, cs: 'gcj02', cat: '地标', aliases: ['亚龙湾', '三亚'] },
  { name: '银川鼓楼', lat: 38.487193, lng: 106.230909, cs: 'gcj02', cat: '地标', aliases: ['鼓楼', '银川'] },
  { name: '西宁东关清真寺', lat: 36.617162, lng: 101.779366, cs: 'gcj02', cat: '地标', aliases: ['东关清真寺', '西宁'] },
  { name: '香港中环', lat: 22.28168, lng: 114.158124, cs: 'gcj02', cat: '城市', aliases: ['中环', '香港'] },
  { name: '澳门大三巴', lat: 22.198751, lng: 113.54909, cs: 'gcj02', cat: '地标', aliases: ['大三巴', '澳门'] },
  { name: '台北101', lat: 25.033611, lng: 121.564558, cs: 'gcj02', cat: '地标', aliases: ['台北101', '台北'] },

  // ---- 境外（WGS-84）----
  { name: '苹果总部', lat: 37.3316, lng: -122.0323, cs: 'wgs84', cat: '境外', aliases: ['Apple', '库比蒂诺'] },
  { name: '谷歌总部', lat: 37.422, lng: -122.084, cs: 'wgs84', cat: '境外', aliases: ['Google', '山景城'] },
  { name: '埃菲尔铁塔', lat: 48.8584, lng: 2.2945, cs: 'wgs84', cat: '境外', aliases: ['巴黎'] },
  { name: '时代广场', lat: 40.758, lng: -73.9855, cs: 'wgs84', cat: '境外', aliases: ['纽约'] },
  { name: '哈利法塔', lat: 25.1972, lng: 55.2744, cs: 'wgs84', cat: '境外', aliases: ['迪拜'] },
  { name: '东京涩谷', lat: 35.6595, lng: 139.7005, cs: 'wgs84', cat: '境外', aliases: ['涩谷', '东京'] },
  { name: '首尔明洞', lat: 37.5636, lng: 126.9827, cs: 'wgs84', cat: '境外', aliases: ['明洞', '首尔'] },
];

function all() {
  return PLACES;
}

function categories() {
  return [...new Set(PLACES.map((p) => p.cat))];
}

/** 关键字检索：名称 / 别名 / 分类 / 模糊包含，支持空格分词 */
function search(keyword) {
  if (!keyword) return PLACES.slice();
  const tokens = String(keyword).trim().toLowerCase().split(/\s+/).filter(Boolean);
  return PLACES.filter((p) => {
    const hay = `${p.name} ${p.cat} ${(p.aliases || []).join(' ')}`.toLowerCase();
    return tokens.every((t) => hay.includes(t));
  });
}

/** 精确匹配：名称完全一致或别名命中 */
function resolve(keyword) {
  const lower = String(keyword).trim().toLowerCase();
  return (
    PLACES.find((p) => p.name.toLowerCase() === lower) ||
    PLACES.find((p) => (p.aliases || []).some((a) => a.toLowerCase() === lower)) ||
    null
  );
}

/** 转为可直接注入的 WGS-84 点位 */
function toPoint(place) {
  const w = toWgs84(place.lat, place.lng, place.cs);
  return {
    lat: w.lat,
    lng: w.lng,
    name: place.name,
    cat: place.cat,
    source: place.cs,
    wgs84: w,
    gcj02: place.cs === 'gcj02' ? { lat: place.lat, lng: place.lng } : null,
  };
}

/** 找出离给定坐标最近的预设点 */
function nearest(point) {
  let best = null;
  let bestD = Infinity;
  for (const p of PLACES) {
    const w = toWgs84(p.lat, p.lng, p.cs);
    const d = distance(point, w);
    if (d < bestD) {
      bestD = d;
      best = p;
    }
  }
  return best ? { place: best, distance: bestD } : null;
}

module.exports = { all, categories, search, resolve, toPoint, nearest };