#!/usr/bin/env node
const { commands, parseArgs, VERSION } = require('../src/cli');
const ui = require('../src/ui');

const argv = process.argv.slice(2);
const { positional, flags } = parseArgs(argv);
const name = positional.shift() || 'help';

const cmd = commands[name];
if (!cmd) {
  ui.warn(`未知命令：${name}`);
  const known = Object.keys(commands).filter((k) => k !== 'help');
  const near = known.filter((k) => k.startsWith(name[0]) || name.startsWith(k[0]));
  if (near.length) ui.warn(`你是不是想用：${near.join(' / ')}`);
  ui.warn('');
  commands.help.run().catch(() => {});
  process.exit(1);
}

if (flags.version) {
  ui.say(`locctl ${VERSION}`);
  process.exit(0);
}

if (flags.help && name !== 'help') {
  ui.say(cmd.usage || `locctl ${name}`);
  process.exit(0);
}

cmd.run({ positional, flags }).catch((err) => {
  ui.warn('');
  ui.warn(`${ui.c.red('失败')} ${err.message}`);
  if (process.env.LOCCTL_DEBUG) ui.warn(err.stack);
  process.exitCode = 1;
});