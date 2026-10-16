'use strict';

const { execFile, spawn } = require('child_process');

const ADB_BIN = process.env.LOCCTL_ADB || 'adb';

/** 支持 LOCCTL_SERIAL / ANDROID_SERIAL 指定设备 */
function serial() {
  return process.env.LOCCTL_SERIAL || process.env.ANDROID_SERIAL || '';
}

function prefix(args) {
  const s = serial();
  return s ? ['-s', s, ...args] : args;
}

class AdbError extends Error {
  constructor(args, stderr) {
    const msg = String(stderr || '').trim().split('\n')[0] || '未知错误';
    super(`adb ${args.join(' ')} 失败：${msg}`);
    this.name = 'AdbError';
    this.args = args;
    this.stderr = stderr;
  }
}

/** 执行一次 adb 命令 */
function adb(args, options = {}) {
  const { allowFail = false, timeout = 20000 } = options;
  return new Promise((resolve, reject) => {
    execFile(
      ADB_BIN,
      prefix(args),
      { timeout, windowsHide: true, maxBuffer: 16 * 1024 * 1024, encoding: 'utf8' },
      (err, stdout, stderr) => {
        if (err) {
          if (allowFail) {
            resolve({ ok: false, out: stdout || '', err: stderr || err.message });
            return;
          }
          reject(new AdbError(args, stderr || err.message));
          return;
        }
        resolve({ ok: true, out: stdout || '', err: stderr || '' });
      },
    );
  });
}

/** 执行并返回 stdout（失败抛错） */
async function adbText(args, options = {}) {
  const r = await adb(args, options);
  return r.out;
}

/** 执行并返回原始结果，不抛错 */
async function adbTry(args, options = {}) {
  return adb(args, { ...options, allowFail: true });
}

/** shell 命令：args 为字符串数组，逐条传给设备 shell */
async function sh(...args) {
  return adbText(['shell', ...args]);
}

/** 读取系统属性 */
async function getprop(name) {
  const r = await adbTry(['shell', 'getprop', name]);
  return r.ok ? r.out.trim() : '';
}

/** 列出在线设备 */
async function devices() {
  const out = await adbText(['devices', '-l']);
  const list = [];
  for (const line of out.split('\n').slice(1)) {
    const t = line.trim();
    if (!t || t.startsWith('*')) continue;
    const [id, state, ...rest] = t.split(/\s+/);
    const meta = {};
    for (const kv of rest) {
      const [k, v] = kv.split(':');
      if (k) meta[k] = v;
    }
    list.push({ id, state, ...meta });
  }
  return list;
}

/**
 * 打开一个常驻的 `adb shell` 会话。
 * 相比每 500ms 启动一次 adb 进程，常驻会话可以把定位推送的
 * 单次开销从约 80ms 降到约 5ms，高频推送场景必须复用它。
 */
function openShell() {
  const proc = spawn(ADB_BIN, prefix(['shell']), {
    stdio: ['pipe', 'pipe', 'pipe'],
    windowsHide: true,
  });

  let buf = '';
  const queue = [];
  const state = { closed: false };

  proc.stdout.setEncoding('utf8');
  proc.stdout.on('data', (chunk) => {
    buf += chunk;
    let idx = buf.indexOf('\n');
    while (idx >= 0) {
      const line = buf.slice(0, idx).replace(/\r$/, '');
      buf = buf.slice(idx + 1);
      const job = queue.shift();
      if (job) job.resolve(line);
      idx = buf.indexOf('\n');
    }
  });

  proc.stderr.setEncoding('utf8');
  const stderrTail = { text: '' };
  proc.stderr.on('data', (chunk) => {
    const lines = String(chunk).trim().split('\n');
    stderrTail.text = lines[lines.length - 1] || '';
  });

  proc.on('close', () => {
    state.closed = true;
    while (queue.length) {
      const job = queue.shift();
      clearTimeout(job.timer);
      job.reject(new Error('adb shell 会话已关闭'));
    }
  });

  return {
    /** 发送一条命令并等待其单行输出 */
    send(cmd, timeoutMs = 15000) {
      if (state.closed) return Promise.reject(new Error('adb shell 会话已关闭'));
      return new Promise((resolve, reject) => {
        const job = { resolve, reject, timer: null };
        job.timer = setTimeout(() => {
          const i = queue.indexOf(job);
          if (i >= 0) queue.splice(i, 1);
          reject(new Error(`命令超时（${timeoutMs}ms）：${cmd}`));
        }, timeoutMs);
        queue.push(job);
        proc.stdin.write(`${cmd}\n`);
      });
    },
    /**
     * 发送一条命令并要求它输出一行。
     * 平台的 cmd location 在成功时不打印任何内容，直接等行会一直等到超时，
     * 因此统一追加 `; echo rc=$?`，用返回码作为唯一确定的输出行。
     */
    async sendChecked(cmd, timeoutMs = 15000) {
      const line = await this.send(`${cmd}; echo rc=$?`, timeoutMs);
      const m = line.match(/rc=(\d+)/);
      if (!m) throw new Error(`命令无返回码：${cmd}`);
      if (m[1] !== '0') {
        throw new Error(`返回码 ${m[1]}${stderrTail.text ? ` — ${stderrTail.text}` : ''}`);
      }
      return line;
    },
    get closed() {
      return state.closed;
    },
    close() {
      if (!state.closed) {
        state.closed = true;
        try {
          proc.stdin.end();
        } catch {
          /* 忽略 */
        }
        try {
          proc.kill();
        } catch {
          /* 忽略 */
        }
      }
    },
  };
}

/** 设备信息快照：型号、系统版本、SDK、root 状态等 */
async function deviceInfo() {
  const [release, sdk, model, brand, manufacturer, abi] = await Promise.all([
    getprop('ro.build.version.release'),
    getprop('ro.build.version.sdk'),
    getprop('ro.product.model'),
    getprop('ro.product.brand'),
    getprop('ro.product.manufacturer'),
    getprop('ro.product.cpu.abi'),
  ]);

  const su = await adbTry(['shell', 'which su']);
  const rooted = su.ok && su.out.trim().length > 0;

  return {
    release,
    sdk: Number(sdk) || 0,
    model: model || 'unknown',
    brand: brand || '',
    manufacturer: manufacturer || '',
    abi: abi || '',
    rooted,
  };
}

module.exports = {
  ADB_BIN,
  AdbError,
  adb,
  adbText,
  adbTry,
  sh,
  getprop,
  devices,
  openShell,
  deviceInfo,
  serial,
};