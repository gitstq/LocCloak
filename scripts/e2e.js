const { spawn } = require('child_process');
const path = require('path');

const CLI = path.join(__dirname, '..', 'bin', 'locctl.js');
const GPX = path.join(__dirname, '..', 'routes', 'west-lake-loop.gpx');
const mock = require('../src/mock');

function run(args, ms) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [CLI, ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    child.stdout.on('data', (d) => {
      out += d.toString();
    });
    child.stderr.on('data', (d) => {
      out += d.toString();
    });
    const killer = setTimeout(() => child.kill(), ms);
    child.on('close', () => {
      clearTimeout(killer);
      resolve(out);
    });
  });
}

async function snapshot(label) {
  const st = await mock.status();
  const bits = Object.keys(st.mocked)
    .map(
      (p) =>
        p +
        '=' +
        (st.mocked[p] ? 'MOCK' : 'real') +
        (st.last[p] ? '@' + st.last[p].lat.toFixed(5) + ',' + st.last[p].lng.toFixed(5) : ''),
    )
    .join('  ');
  console.log('  [' + label + '] ' + bits);
  return st;
}

(async () => {
  console.log('1) hold 常驻推送（2.5s）');
  const holdOut = await run(
    ['hold', '39.9078,116.3912', '--interval', '300', '--accuracy', '3'],
    2500,
  );
  console.log(holdOut.includes('常驻推送') ? '   hold 已进入常驻模式 OK' : '   hold FAIL');
  console.log(
    holdOut.includes('推送失败')
      ? '   常驻推送报错 FAIL: ' + holdOut.slice(holdOut.indexOf('推送失败'))
      : '   常驻推送无报错 OK',
  );
  await snapshot('after hold');

  console.log('2) path 西湖轨迹（快速回放 6s）');
  const pathOut = await run(['path', GPX, '--duration', '6', '--interval', '400'], 30000);
  console.log(pathOut.includes('轨迹播放结束') ? '   轨迹播放完整结束 OK' : '   轨迹播放 FAIL');
  if (pathOut.includes('推送中断') || pathOut.includes('失败')) {
    console.log('   轨迹推送出现错误 FAIL');
    console.log(pathOut);
  }
  const st2 = await snapshot('after path');

  console.log('3) wander 漫游（2.5s）');
  await run(['wander', '--radius', '200', '--interval', '400'], 2500);
  await snapshot('after wander');

  console.log('4) 校验 path 终点是否落在西湖范围内');
  const p = st2.last.gps;
  const inWestLake = p && p.lat > 30.2 && p.lat < 30.28 && p.lng > 120.12 && p.lng < 120.18;
  console.log('   终点 ' + (p ? p.lat + ', ' + p.lng : '无') + ' -> ' + (inWestLake ? 'OK' : 'FAIL'));

  console.log('5) stop 还原');
  await run(['stop'], 20000);
  const st3 = await snapshot('after stop');
  const restored = Object.values(st3.mocked).every((v) => v === false);
  console.log('   真实定位源恢复 -> ' + (restored ? 'OK' : 'FAIL'));
})();