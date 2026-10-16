'use strict';

const { adb, adbTry, adbText, devices, deviceInfo, openShell } = require('./adb');

/**
 * 模拟定位注入层
 *
 * 原理同 VirtualLocation 的 ADB 方案：把系统的定位提供者替换为 test provider，
 * 再周期性向其写入位置。
 *
 * 针对本机（Redmi / Android 16 / HyperOS，无 root）做了三点关键适配：
 *
 * 1. Android 12(API 31) 起，shell 身份默认没有 MOCK_LOCATION 权限，
 *    必须先 `appops set com.android.shell android:mock_location allow`。
 *    这替代了老版本 VirtualLocation 里「开发者选项 -> 选择模拟位置应用」的手工步骤。
 *
 * 2. 国内应用（微信 / QQ / 钉钉）绝大多数走 network 或 fused provider，
 *    只 mock gps 会完全无效。这里同时接管 gps + network + fused 三个源。
 *
 * 3. 单条注入命令无法携带 bearing / speed / altitude —— 平台的
 *    LocationShellCommand 只开放了纬度、经度、精度、时间四个参数，
 *    因此本工具不提供这三个字段，避免给出无效承诺。
 */

const DEFAULT_PROVIDERS = ['gps', 'network', 'fused'];

const PROVIDER_FLAGS = {
  gps: [
    '--requiresSatellite',
    '--supportsAltitude',
    '--supportsSpeed',
    '--supportsBearing',
    '--powerRequirement',
    '3',
  ],
  network: [
    '--requiresNetwork',
    '--requiresCell',
    '--supportsAltitude',
    '--supportsSpeed',
    '--supportsBearing',
  ],
  fused: [],
  passive: [],
};

class MockError extends Error {
  constructor(message) {
    super(message);
    this.name = 'MockError';
  }
}

/** 环境自检 + 自动修复，返回设备能力描述 */
async function doctor({ fix = true, quiet = false } = {}) {
  const log = quiet ? () => {} : (m) => process.stderr.write(`${m}\n`);
  const notes = [];

  const list = await devices();
  const online = list.filter((d) => d.state === 'device');
  if (online.length === 0) {
    throw new MockError(
      `没有可用设备，请检查 USB 调试。\n${list.map((d) => `  ${d.id}  ${d.state}`).join('\n')}`,
    );
  }

  const info = await deviceInfo();
  log(`设备      ${info.manufacturer} ${info.brand} ${info.model} (${info.abi})`);
  log(`系统      Android ${info.release} / API ${info.sdk}`);
  log(`root      ${info.rooted ? '有' : '无 —— 仅 ADB 注入模式可用'}`);

  if (info.sdk >= 31) {
    notes.push(`Android ${info.release}：走 appops 授权，无需在开发者选项里选模拟位置应用`);
  } else {
    notes.push('系统较老：需在「开发者选项 -> 位置信息 -> 选择模拟位置应用」中勾选宿主应用');
  }

  const op = await adbTry([
    'shell',
    'appops',
    'get',
    'com.android.shell',
    'android:mock_location',
  ]);
  let granted = /:\s*allow/i.test(op.out);
  if (!granted && fix) {
    await adb([
      'shell',
      'appops',
      'set',
      'com.android.shell',
      'android:mock_location',
      'allow',
    ]);
    granted = true;
    log('已自动授予 com.android.shell 的 android:mock_location 权限');
  }
  log(`MOCK_LOCATION ${granted ? '已授予' : '未授予（注入会被 SecurityException 拒绝）'}`);
  if (!granted) notes.push('执行 `locctl doctor --fix` 授予权限');

  const enabled = (await adbTry(['shell', 'cmd', 'location', 'is-location-enabled'])).out.trim();
  log(`定位总开关 ${enabled === 'true' ? '开启' : `关闭（${enabled}）`}`);
  if (enabled !== 'true') {
    notes.push('定位总开关关闭，执行 `locctl on` 打开');
    if (fix) {
      await adb(['shell', 'cmd', 'location', 'set-location-enabled', 'true']);
      log('已自动打开定位总开关');
    }
  }

  const nlp = await adbTry(['shell', 'pm', 'list', 'packages', 'com.xiaomi.metoknlp']);
  if (nlp.ok && nlp.out.includes('com.xiaomi.metoknlp')) {
    notes.push('检测到小米 metoknlp（网络定位）：mock network provider 后其输出会被完全遮蔽');
  }

  return { info, granted, locationEnabled: enabled === 'true', notes, devices: online };
}

/** 检查某个 provider 是否已是 mock 状态 */
async function isMocked(provider) {
  const out = await adbText(['shell', 'dumpsys', 'location'], { timeout: 30000 });
  const idx = out.indexOf(`${provider} provider`);
  if (idx < 0) return false;
  return out.slice(idx, idx + 40).includes('[mock]');
}

/** 打开系统定位总开关 */
async function enableLocation() {
  await adb(['shell', 'cmd', 'location', 'set-location-enabled', 'true']);
}

/**
 * 安装 mock provider。
 * 已存在时先移除再重建，避免残留旧的 ProviderProperties。
 */
async function install(providers = DEFAULT_PROVIDERS, { quiet = false } = {}) {
  const log = quiet ? () => {} : (m) => process.stdout.write(`${m}\n`);

  await adb([
    'shell',
    'appops',
    'set',
    'com.android.shell',
    'android:mock_location',
    'allow',
  ]);

  for (const p of providers) {
    await adb(['shell', 'cmd', 'location', 'providers', 'remove-test-provider', p], {
      allowFail: true,
    });
    const args = [
      'shell',
      'cmd',
      'location',
      'providers',
      'add-test-provider',
      p,
      ...(PROVIDER_FLAGS[p] || []),
    ];
    const r = await adb(args, { allowFail: true });
    if (!r.ok) {
      throw new MockError(`添加 mock provider "${p}" 失败：${r.err.trim()}`);
    }
    await adb(['shell', 'cmd', 'location', 'providers', 'set-test-provider-enabled', p, 'true']);
    log(`  [ok] ${p} provider 已接管`);
  }
}

/** 移除 mock provider，交还真实定位源 */
async function uninstall(providers = DEFAULT_PROVIDERS, { quiet = false } = {}) {
  const log = quiet ? () => {} : (m) => process.stdout.write(`${m}\n`);
  for (const p of providers) {
    const r = await adb(
      ['shell', 'cmd', 'location', 'providers', 'remove-test-provider', p],
      { allowFail: true },
    );
    log(r.ok ? `  [ok] ${p} provider 已还原` : `  [--] ${p} provider 不处于 mock 状态，跳过`);
  }
}

function locationArgs(point, accuracy, time) {
  return [
    '--location',
    `${point.lat.toFixed(7)},${point.lng.toFixed(7)}`,
    '--accuracy',
    String(accuracy),
    '--time',
    String(time == null ? Date.now() : time),
  ];
}

/** 注入一次位置（每次单独起 adb 进程，适合非高频场景） */
async function pushOnce(point, options = {}) {
  const { providers = DEFAULT_PROVIDERS, accuracy = 5, time } = options;
  const args = locationArgs(point, accuracy, time);
  for (const p of providers) {
    await adb(['shell', 'cmd', 'location', 'providers', 'set-test-provider-location', p, ...args]);
  }
  return point;
}

/**
 * 高频推送器：复用一个常驻 adb shell 会话，按固定间隔刷新位置。
 *
 * 这是 VirtualLocation 中 mMockThread 的等价实现。持续刷新有两个作用：
 *   1. 部分应用会按 elapsedRealtime 校验定位时间戳，一次性注入很快会「过期」；
 *   2. 覆盖厂商 ROM 的网络定位回填。
 */
class Pusher {
  constructor(providers = DEFAULT_PROVIDERS) {
    this.providers = providers;
    this.shell = null;
    this.timer = null;
    this.interval = 500;
    this.accuracy = 5;
    this.point = null;
    this.pushes = 0;
    this.errors = 0;
    this.running = false;
  }

  async push(point) {
    if (!this.shell || this.shell.closed) {
      this.shell = openShell();
    }
    const args = locationArgs(point, this.accuracy);
    await Promise.all(
      this.providers.map((p) =>
        this.shell.sendChecked(
          `cmd location providers set-test-provider-location ${p} ${args.join(' ')}`,
        ),
      ),
    );
    this.point = point;
    this.pushes += 1;
  }

  /**
   * @param {object} point 初始位置
   * @param {object} opts interval 推送间隔(ms)、accuracy 精度(m)、onTick 每 tick 返回新位置
   */
  start(point, { interval = 500, accuracy = 5, onTick = null } = {}) {
    this.interval = interval;
    this.accuracy = accuracy;
    this.running = true;
    this.point = point;

    const tick = async () => {
      if (!this.running) return;
      try {
        if (onTick) {
          const next = await onTick(this.point);
          if (next) this.point = next;
        }
        await this.push(this.point);
        this.errors = 0;
      } catch (err) {
        this.errors += 1;
        if (this.shell) this.shell.close();
        this.shell = null;
        if (this.errors <= 3) {
          process.stderr.write(`推送失败（连续 ${this.errors} 次）：${err.message}\n`);
        }
      }
      if (this.running) this.timer = setTimeout(tick, this.interval);
    };

    tick();
    return this;
  }

  stop() {
    this.running = false;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    if (this.shell) this.shell.close();
    this.shell = null;
  }
}

/** 读取当前 mock 状态快照 */
async function status() {
  // dumpsys 在 Windows 上返回 CRLF，而 JS 正则里的 . 不匹配 \r，先归一化换行
  const raw = await adbText(['shell', 'dumpsys', 'location'], { timeout: 40000 });
  const out = raw.replace(/\r\n/g, '\n');
  const result = { mocked: {}, last: {}, locationEnabled: null };

  const en = await adbTry(['shell', 'cmd', 'location', 'is-location-enabled']);
  result.locationEnabled = en.out.trim() === 'true';

  for (const p of ['gps', 'network', 'fused']) {
    const i = out.indexOf(`${p} provider`);
    if (i < 0) continue;
    result.mocked[p] = out.slice(i, i + 40).includes('[mock]');
    const chunk = out.slice(i, i + 4000);
    const m = chunk.match(
      new RegExp(
        `${p} provider[^\\n]*\\n(?:.*\\n){0,8}?.*last(?: mock)? location=Location\\[${p} ([-\\d.]+),([-\\d.]+)`,
      ),
    );
    if (m) result.last[p] = { lat: Number(m[1]), lng: Number(m[2]) };
  }
  return result;
}

module.exports = {
  DEFAULT_PROVIDERS,
  PROVIDER_FLAGS,
  MockError,
  doctor,
  install,
  uninstall,
  pushOnce,
  isMocked,
  enableLocation,
  status,
  Pusher,
};