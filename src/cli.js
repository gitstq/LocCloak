'use strict';

const ui = require('./ui');
const { parsePoint, toWgs84, convert, distance } = require('./coords');
const presets = require('./presets');
const gpx = require('./gpx');
const mock = require('./mock');
const { adb, adbText, devices } = require('./adb');

const { c, say, warn, title, kv, ok, fail, warnLine, hint, fmtPoint, fmtDistance } = ui;

const VERSION = '2.0.0';

/* ------------------------------------------------------------------ 参数解析 */

function parseArgs(argv) {
  const positional = [];
  const flags = {};
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (!a.startsWith('--')) {
      positional.push(a);
      continue;
    }
    const body = a.slice(2);
    const eq = body.indexOf('=');
    if (eq >= 0) {
      flags[body.slice(0, eq)] = body.slice(eq + 1);
    } else if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) {
      flags[body] = argv[i + 1];
      i += 1;
    } else {
      flags[body] = true;
    }
  }
  return { positional, flags };
}

function num(flags, key, dflt) {
  const v = flags[key];
  if (v === undefined) return dflt;
  const n = Number(v);
  if (!Number.isFinite(n)) throw new Error(`--${key} 需要一个数字，收到 "${v}"`);
  return n;
}

function pickProviders(flags) {
  const allowed = [...mock.DEFAULT_PROVIDERS, 'passive'];
  if (!flags.providers) return [...mock.DEFAULT_PROVIDERS];
  const list = String(flags.providers)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  for (const p of list) {
    if (!allowed.includes(p)) throw new Error(`不支持的 provider "${p}"，可选：${allowed.join(',')}`);
  }
  return list.length ? list : [...mock.DEFAULT_PROVIDERS];
}

/* -------------------------------------------------------------- 共用工具函数 */

/** 把用户给的坐标转成可直接注入的 WGS-84 点位 */
function resolvePoint(input, cs) {
  const p = parsePoint(input, cs || 'wgs84');
  return toWgs84(p.lat, p.lng, p.cs);
}

/** 确保目标 provider 已被接管，按需安装 */
async function ensureMocked(providers, { quiet = true } = {}) {
  const st = await mock.status();
  const missing = providers.filter((p) => !st.mocked[p]);
  if (missing.length === 0) return { installed: false, st };
  if (!quiet) say(c.gray(`  接管 provider：${missing.join(', ')}`));
  await mock.install(providers, { quiet: true });
  return { installed: true, st: await mock.status() };
}

function showLocation(point, extra = {}) {
  title('当前注入位置');
  if (point.name) kv('地点', c.bold(point.name));
  kv('WGS-84', point.gcj02 ? fmtPoint(point) : `${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}`);
  if (point.gcj02) kv('GCJ-02', `${point.gcj02.lat.toFixed(6)}, ${point.gcj02.lng.toFixed(6)}`);
  if (extra.accuracy != null) kv('精度', `${extra.accuracy} m`);
  if (extra.note) kv('备注', extra.note);
  say('');
}

/* -------------------------------------------------------------------- 命令 */

const commands = {};

commands.doctor = {
  desc: '环境自检并自动修复权限',
  usage: 'locctl doctor [--no-fix]',
  async run({ flags }) {
    const r = await mock.doctor({ fix: flags['no-fix'] !== true });
    if (r.notes.length) {
      title('注意事项');
      for (const n of r.notes) warnLine(n);
    }
    title('结论');
    if (r.granted && r.locationEnabled) ok('可以开始注入');
    else fail('环境未就绪，请按上面的提示处理');
    say('');
  },
};

commands.on = {
  desc: '打开系统定位总开关',
  usage: 'locctl on',
  async run() {
    await mock.enableLocation();
    ok('定位总开关已打开');
  },
};

commands.off = {
  desc: '关闭系统定位总开关',
  usage: 'locctl off',
  async run() {
    await adb(['shell', 'cmd', 'location', 'set-location-enabled', 'false']);
    ok('定位总开关已关闭');
  },
};

commands.status = {
  desc: '查看 mock 状态与最近定位',
  usage: 'locctl status',
  async run() {
    const st = await mock.status();
    title('模拟定位状态');
    kv('定位总开关', st.locationEnabled ? c.green('开启') : c.red('关闭'));
    for (const p of Object.keys(st.mocked)) {
      const m = st.mocked[p];
      const last = st.last[p];
      const pos = last ? `  ${last.lat.toFixed(6)}, ${last.lng.toFixed(6)}` : '';
      kv(p, m ? c.yellow('已接管') + pos : c.gray('真实源'));
    }
    say('');
    if (!Object.values(st.mocked).some(Boolean)) {
      hint('当前没有任何 mock provider，执行 `locctl goto 杭州西湖` 开始');
    }
  },
};

commands.list = {
  desc: '列出内置地点',
  usage: 'locctl list [关键词]',
  async run({ positional }) {
    const kw = positional.join(' ');
    const list = presets.search(kw);
    title(kw ? `匹配「${kw}」的地点（${list.length}）` : `全部内置地点（${list.length}）`);
    for (const p of list) {
      const w = presets.toPoint(p);
      say(
        `  ${c.bold(p.name.padEnd(12))} ${c.gray(p.cat.padEnd(4))} ` +
          `${c.green(`${w.lat.toFixed(5)}, ${w.lng.toFixed(5)}`)} ` +
          c.gray(`(${p.cs})`),
      );
    }
    say('');
  },
};

commands.goto = {
  desc: '跳转到内置地点',
  usage: 'locctl goto <关键词> [--accuracy 5]',
  async run({ positional, flags }) {
    const kw = positional.join(' ');
    if (!kw) throw new Error('请提供地点关键词，例如：locctl goto 杭州西湖');

    let place = presets.resolve(kw);
    if (!place) {
      const hits = presets.search(kw);
      if (hits.length === 0) {
        throw new Error(`内置地点里没有「${kw}」。可以：\n  - 直接给坐标  locctl set <lat,lng>\n  - 查看全部  locctl list`);
      }
      if (hits.length > 1) {
        warn(`「${kw}」匹配到多个地点，取第一个：${hits[0].name}`);
        say(c.gray(`  其它候选：${hits.slice(1, 6).map((h) => h.name).join('、')}`));
      }
      place = hits[0];
    }

    const point = presets.toPoint(place);
    const providers = pickProviders(flags);
    await ensureMocked(providers);

    const accuracy = num(flags, 'accuracy', 5);
    await mock.pushOnce(point, { providers, accuracy });

    showLocation(point, { accuracy, note: `来源 ${place.name}（${place.cs} 已转 WGS-84）` });
    ok('已注入。位置会一直保持，执行 `locctl stop` 恢复真实定位');
    hint('如需常驻刷新（部分应用会校验时间戳），执行 `locctl hold`');
  },
};

commands.set = {
  desc: '注入任意坐标（或内置地名）',
  usage: 'locctl set <lat,lng|地名> [--cs wgs84|gcj02|bd09] [--accuracy 5]',
  async run({ positional, flags }) {
    const raw = positional.join(' ');
    if (!raw) throw new Error('请提供坐标，例如：locctl set 30.274602,120.211963 --cs gcj02');

    // 数字开头按坐标解析，否则回退到内置地名，省得用户记忆两套命令
    const looksLikeCoord = /^[-+]?\d*\.?\d+\s*[,;\s]\s*[-+]?\d*\.?\d+/.test(raw);
    let point;
    let note;

    if (looksLikeCoord) {
      const cs = flags.cs || 'wgs84';
      const parsed = parsePoint(raw, cs);
      point = toWgs84(parsed.lat, parsed.lng, parsed.cs);
      note = `输入坐标系 ${parsed.cs}${parsed.cs === 'wgs84' ? '' : '，已转换为 WGS-84'}`;
    } else {
      const place = presets.resolve(raw) || presets.search(raw)[0];
      if (!place) {
        throw new Error(`既不是合法坐标，也没匹配到内置地点「${raw}」。用 locctl list 查看内置地点`);
      }
      point = presets.toPoint(place);
      note = `按地名匹配到「${place.name}」（${place.cs} 已转 WGS-84）`;
    }

    const providers = pickProviders(flags);
    await ensureMocked(providers);
    const accuracy = num(flags, 'accuracy', 5);
    await mock.pushOnce(point, { providers, accuracy });

    showLocation(point, { accuracy, note });
    ok('已注入。位置会一直保持，执行 `locctl stop` 恢复真实定位');
  },
};

commands.convert = {
  desc: '坐标系换算（离线）',
  usage: 'locctl convert <lat,lng> [--from gcj02] [--to wgs84]',
  async run({ positional, flags }) {
    const raw = positional.join(' ');
    if (!raw) throw new Error('请提供坐标，例如：locctl convert 30.274602,120.211963 --from gcj02');
    const from = flags.from || flags.cs || 'gcj02';
    const to = flags.to || 'wgs84';

    const p = parsePoint(raw, from);
    const out = convert(p.lat, p.lng, from, to);
    const wgs = convert(p.lat, p.lng, from, 'wgs84');
    const shift = distance({ lat: p.lat, lng: p.lng }, wgs);

    title('坐标系换算');
    kv('输入', `${p.lat.toFixed(7)}, ${p.lng.toFixed(7)}   ${c.gray(p.cs)}`);
    kv(`输出(${to})`, c.bold(`${out.lat.toFixed(7)}, ${out.lng.toFixed(7)}`));
    kv('偏移量', fmtDistance(shift));
    say('');
    hint('注入设备时统一用 WGS-84，国内应用会在内部再转成 GCJ-02');
  },
};

commands.hold = {
  desc: '常驻循环推送（对抗时间戳校验）',
  usage: 'locctl hold [lat,lng] [--interval 500]',
  async run({ positional, flags }) {
    const raw = positional.join(' ');
    let point;
    let label = '自定义坐标';

    if (raw) {
      point = resolvePoint(raw, flags.cs);
    } else {
      const place = presets.resolve(flags.at || '天安门') || presets.all()[0];
      point = presets.toPoint(place);
      label = place.name;
    }

    const providers = pickProviders(flags);
    const interval = num(flags, 'interval', 500);
    const accuracy = num(flags, 'accuracy', 5);

    await ensureMocked(providers);
    showLocation(point, { accuracy, note: `常驻推送，每 ${interval} ms 刷新` });
    say(`  ${c.gray('目标')}    ${label}`);
    say(`  ${c.gray('停止')}    Ctrl+C`);
    say('');

    const pusher = new mock.Pusher(providers);
    pusher.start(point, { interval, accuracy });

    const report = setInterval(() => {
      if (process.stdout.isTTY) {
        process.stdout.write(`\r  ${c.gray('已推送')} ${pusher.pushes} 次  `);
      }
    }, 1000);
    report.unref?.();

    const shutdown = () => {
      clearInterval(report);
      pusher.stop();
      say('');
      ok(`已停止常驻推送，共 ${pusher.pushes} 次`);
      say(c.gray('  mock provider 仍然生效，位置保持不变；执行 `locctl stop` 完全还原'));
      process.exitCode = 0;
    };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  },
};

commands.path = {
  desc: '按 GPX 轨迹播放',
  usage: 'locctl path <file.gpx> [--speed 24] [--cs wgs84]',
  async run({ positional, flags }) {
    const file = positional[0];
    if (!file) throw new Error('请提供 GPX 文件路径，例如：locctl path routes/hangzhou-west-lake.gpx');

    const { points } = gpx.load(file);
    const st = gpx.stats(points);
    const cs = flags.cs || 'wgs84';

    const wgsPoints = points.map((p) => {
      const w = toWgs84(p.lat, p.lng, cs);
      return { ...w, raw: p };
    });

    title('轨迹');
    kv('轨迹点', `${st.count} 个`);
    kv('总里程', fmtDistance(st.totalMeters));

    const walkKmh = num(flags, 'speed', 5);
    const bySpeed = Math.max(1, (st.totalMeters / 1000 / walkKmh) * 3600);
    const totalSeconds = flags.speed
      ? bySpeed
      : num(flags, 'duration', Math.max(2, Math.min(600, bySpeed)));

    kv('速度', `${walkKmh} km/h${flags.speed ? '' : '（默认值，可用 --speed 调整）'}`);
    kv('预计时长', `${Math.round(totalSeconds)} s${flags.speed ? '' : '（可用 --duration 覆盖）'}`);
    say('');

    const providers = pickProviders(flags);
    const interval = num(flags, 'interval', 1000);
    const accuracy = num(flags, 'accuracy', 5);
    await ensureMocked(providers);

    const { path: seq } = gpx.resample(wgsPoints, totalSeconds, interval / 1000);
    const stepMs = Math.max(50, (seq.length > 1 ? totalSeconds / (seq.length - 1) : 1) * 1000);

    say(c.gray(`  播放 ${seq.length} 个采样点，约 ${Math.round(totalSeconds)} s，Ctrl+C 结束`));
    say('');

    let i = 0;
    const pusher = new mock.Pusher(providers);
    await pusher.push(seq[0]);
    say(`  ${c.green('●')} 起点  ${seq[0].lat.toFixed(6)}, ${seq[0].lng.toFixed(6)}`);

    const timer = setInterval(async () => {
      i += 1;
      if (i >= seq.length) {
        clearInterval(timer);
        const end = seq[seq.length - 1];
        say('');
        say(`  ${c.red('●')} 终点  ${end.lat.toFixed(6)}, ${end.lng.toFixed(6)}`);
        ok('轨迹播放结束');
        pusher.stop();
                say(c.gray('  mock provider 仍然生效，位置停在终点；执行 `locctl stop` 完全还原'));
                return;
              }
              try {
                await pusher.push(seq[i]);
                const done = Math.round((i / (seq.length - 1)) * 100);
                const travelled = distance(seq[0], seq[i]);
                if (process.stdout.isTTY) {
                  process.stdout.write(
                    `\r  ${c.cyan('→')} ${done}%  ${seq[i].lat.toFixed(6)}, ${seq[i].lng.toFixed(6)}  ${fmtDistance(travelled)}   `,
                  );
                }
              } catch (err) {
                clearInterval(timer);
                warn(`推送中断：${err.message}`);
                process.exitCode = 1;
              }
            }, stepMs);

            process.on('SIGINT', () => {
              clearInterval(timer);
              pusher.stop();
              say('');
              ok('已停止播放');
              process.exitCode = 0;
            });
  },
};

commands.wander = {
  desc: '在半径内随机漫游',
  usage: 'locctl wander [lat,lng] [--radius 300]',
  async run({ positional, flags }) {
    const raw = positional.join(' ');
    let center;
    if (raw) {
      const cs = flags.cs || 'wgs84';
      const p = parsePoint(raw, cs);
      center = toWgs84(p.lat, p.lng, p.cs);
    } else {
      center = presets.toPoint(presets.all()[0]);
    }

    const radius = num(flags, 'radius', 300);
    const providers = pickProviders(flags);
    const interval = num(flags, 'interval', 3000);
    await ensureMocked(providers);

    showLocation(center, { accuracy: num(flags, 'accuracy', 20), note: `半径 ${radius} m 随机漫游` });

    // WGS-84 下 1 度纬度约 111.32 km，经度随纬度收缩
    const mPerDegLat = 111320;
    const mPerDegLng = 111320 * Math.cos((center.lat * Math.PI) / 180);

    const pusher = new mock.Pusher(providers);
    pusher.start(center, {
      interval,
      accuracy: num(flags, 'accuracy', 20),
      onTick: (cur) => {
        const r = radius * Math.sqrt(Math.random());
        const th = Math.random() * Math.PI * 2;
        return {
          lat: cur.lat + (r * Math.sin(th)) / mPerDegLat,
          lng: cur.lng + (r * Math.cos(th)) / mPerDegLng,
        };
      },
    });

    say(c.gray('  Ctrl+C 结束'));
    say('');
    const shutdown = () => {
      pusher.stop();
      ok('已停止漫游');
      process.exitCode = 0;
    };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  },
};

commands.monitor = {
  desc: '实时监控设备上报的定位',
  usage: 'locctl monitor [--interval 2000]',
  async run({ flags }) {
    const interval = num(flags, 'interval', 2000);
    title('定位监控');
    say(c.gray('  Ctrl+C 结束'));
    say('');
    let prev = '';
    const loop = async () => {
      try {
        const st = await mock.status();
        const row = Object.keys(st.mocked)
          .map((p) => `${p}:${st.mocked[p] ? 'MOCK' : 'real'}${
            st.last[p] ? `(${st.last[p].lat.toFixed(4)},${st.last[p].lng.toFixed(4)})` : ''
          }`)
          .join('  ');
        if (row !== prev) {
          say(`  ${new Date().toLocaleTimeString()}  ${row}`);
          prev = row;
        }
      } catch (err) {
        say(`  ${c.red('读取失败')} ${err.message}`);
      }
      const t = setTimeout(loop, interval);
      process.once('SIGINT', () => clearTimeout(t));
    };
    await loop();
  },
};

commands.stop = {
  desc: '移除 mock provider，恢复真实定位',
  usage: 'locctl stop',
  async run({ flags }) {
    const providers = pickProviders(flags);
    title('还原');
    await mock.uninstall(providers);
    say('');
    ok('已移除 mock provider，设备回到真实定位');
    if (!flags.keep) {
      hint('若某些应用仍显示旧位置，重启应用或重启手机即可');
    }
  },
};

commands.help = {
  desc: '显示帮助',
  usage: 'locctl help',
  async run() {
    title(`locctl v${VERSION} — ADB 虚拟定位`);
    const keys = Object.keys(commands);
    const width = Math.max(...keys.map((k) => k.length));
    for (const k of keys) {
      say(`  ${c.cyan(k.padEnd(width))}  ${commands[k].desc}`);
    }
    say('');
    title('全局参数');
    const globals = [
      ['--cs', '坐标系 wgs84 | gcj02 | bd09（默认 wgs84）'],
      ['--accuracy', '上报精度（米），默认 5；数值越小看起来越精确'],
      ['--providers', '接管的 provider，默认 gps,network,fused'],
      ['--interval', '推送间隔（毫秒）'],
    ];
    for (const [k, v] of globals) say(`  ${c.cyan(k.padEnd(width))}  ${v}`);
    say('');
    hint('示例：locctl goto 杭州西湖   |   locctl set 30.27,120.21 --cs gcj02   |   locctl path routes/demo.gpx');
  },
};

module.exports = { commands, parseArgs, VERSION, devices, adbText };