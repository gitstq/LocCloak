// Spike 验证：在设备上用 Node 直接运行现有业务代码
const c = require('./js/coords');
const g = require('./js/gpx');
const p = require('./js/presets');

const w = c.gcj02ToWgs84(30.274602, 120.211963);
console.log('coords gcj02->wgs84:', w.lat.toFixed(7), w.lng.toFixed(7));

const r = g.load('./west-lake-loop.gpx');
const st = g.stats(r.points);
console.log('gpx points:', st.count, 'len:', (st.totalMeters / 1000).toFixed(2), 'km');

console.log('presets:', p.all().length, '| search 杭州:', p.search('杭州').map((x) => x.name).join(','));

const t = p.toPoint(p.resolve('杭州西湖'));
console.log('toPoint 西湖 wgs84:', t.lat.toFixed(6), t.lng.toFixed(6), '| gcj02:', t.gcj02.lat.toFixed(6), t.gcj02.lng.toFixed(6));

console.log('node version:', process.version, '| arch:', process.arch);