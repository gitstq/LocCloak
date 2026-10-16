'use strict';

const noColor = process.env.NO_COLOR !== undefined || process.env.TERM === 'dumb';

const ESC = String.fromCharCode(27);
const wrap =
  (open, close) =>
  (s) =>
    noColor ? String(s) : `${ESC}[${open}m${s}${ESC}[${close}m`;

const c = {
  bold: wrap(1, 22),
  dim: wrap(2, 22),
  red: wrap(31, 39),
  green: wrap(32, 39),
  yellow: wrap(33, 39),
  blue: wrap(34, 39),
  magenta: wrap(35, 39),
  cyan: wrap(36, 39),
  gray: wrap(90, 39),
};

function say(...args) {
  process.stdout.write(`${args.join(' ')}\n`);
}

function warn(...args) {
  process.stderr.write(`${args.join(' ')}\n`);
}

function title(text) {
  say('');
  say(c.bold(c.cyan(text)));
  say(c.gray('─'.repeat(Math.max(8, text.length * 2))));
}

function kv(key, value) {
  say(`  ${c.gray(String(key).padEnd(12))} ${value}`);
}

function ok(text) {
  say(`  ${c.green('OK')} ${text}`);
}

function fail(text) {
  say(`  ${c.red('XX')} ${text}`);
}

function warnLine(text) {
  say(`  ${c.yellow('!')} ${text}`);
}

function hint(text) {
  say(c.gray(`    ${text}`));
}

/** 坐标展示：同时给出 WGS-84 / GCJ-02 两套值，便于和高德、微信里的数字对照 */
function fmtPoint(point, cs) {
  const parts = [`${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}`];
  if (cs === 'gcj02' && point.gcj02) {
    parts.push(`GCJ-02 ${point.gcj02.lat.toFixed(6)}, ${point.gcj02.lng.toFixed(6)}`);
  }
  return parts.join('    ');
}

function fmtDistance(m) {
  if (!Number.isFinite(m)) return '-';
  if (m < 1000) return `${m.toFixed(0)} m`;
  return `${(m / 1000).toFixed(2)} km`;
}

/** 简易 readline 封装，REPL 用 */
function prompt(question = 'locctl> ') {
  return new Promise((resolve) => {
    process.stdout.write(c.cyan(question));
    process.stdin.setEncoding('utf8');
    process.stdin.resume();
    process.stdin.once('data', (buf) => {
      process.stdin.pause();
      resolve(buf.toString('utf8').trim());
    });
  });
}

module.exports = {
  c,
  noColor,
  say,
  warn,
  title,
  kv,
  ok,
  fail,
  warnLine,
  hint,
  fmtPoint,
  fmtDistance,
  prompt,
};