// 한 단계(STAGE)의 결과 폴더를 모아 시나리오·부하 지점별 3회 중앙값 표(markdown)를 만든다.
//   node loadtest/scripts/summarize.mjs loadtest/results/00_기준선
// 출력은 화면과 {STAGE 폴더}/summary.md. 이 표를 성능측정_기록.md 에 옮긴다.
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const stageDir = process.argv[2];
if (!stageDir) {
  console.error('사용법: node summarize.mjs <results/STAGE 폴더>');
  process.exit(1);
}

const read = (p) => (existsSync(p) ? readFileSync(p, 'utf8') : null);
const json = (p) => {
  const s = read(p);
  return s ? JSON.parse(s) : null;
};

// 시나리오별 대표 응답 시간 지표
const PRIMARY = {
  A: 'http_req_duration{phase:measure,name:match}',
  B: 'click_new_duration{phase:measure}',
  C: null,
  D: 'click_new_duration{phase:measure}',
};
const SECONDARY = {
  B: 'http_req_duration{phase:measure,name:match}',
  D: 'http_req_duration{phase:measure,flow:impression,name:match}',
};

function parseRun(dir) {
  const m = /^([A-D])(?:_(.+))?_run(.+)$/.exec(dir);
  if (!m) return null;
  const [, scenario, label = '', run] = m;
  const path = join(stageDir, dir);
  const k6 = json(join(path, 'k6-summary.json'))?.metrics ?? {};
  const sse = json(join(path, 'sse.json'));
  const locks = read(join(path, 'locks-summary.txt')) ?? '';
  const app = read(join(path, 'app-metrics.txt')) ?? '';
  const metric = (k, f) => (k && k6[k] ? k6[k][f] : undefined);
  const appVal = (name) => {
    const v = new RegExp(`${name}_max=([0-9.e-]+)`).exec(app);
    return v ? Number(v[1]) : undefined;
  };
  const p = PRIMARY[scenario];
  return {
    group: label ? `${scenario} ${label}` : scenario,
    run,
    p50: metric(p, 'p(50)'),
    p95: metric(p, 'p(95)'),
    p99: metric(p, 'p(99)'),
    matchP95: metric(SECONDARY[scenario], 'p(95)'),
    errPct: k6['http_req_failed{phase:measure}'] ? 100 * k6['http_req_failed{phase:measure}'].value : undefined,
    dropped: metric('dropped_iterations{phase:measure}', 'count'),
    dup: metric('click_duplicate{phase:measure}', 'count'),
    lockMax: Number(/max=(\d+)/.exec(locks)?.[1] ?? NaN),
    lockAvg: Number(/avg=([\d.]+)/.exec(locks)?.[1] ?? NaN),
    pendingMax: appVal('hikaricp_connections_pending'),
    cpuMax: appVal('process_cpu_usage'),
    sseEvents: sse?.eventsPerSec,
    sseExpected: sse?.expectedEventsPerSec,
    sseDelayP95: sse?.eventDelayMs?.p95,
    sseFailed: sse ? sse.connectFailed + sse.disconnectedEarly : undefined,
  };
}

function median(values) {
  const v = values.filter((x) => typeof x === 'number' && !Number.isNaN(x)).sort((a, b) => a - b);
  if (!v.length) return undefined;
  const mid = Math.floor(v.length / 2);
  return v.length % 2 ? v[mid] : (v[mid - 1] + v[mid]) / 2;
}

const fmt = (x, d = 1) => (x === undefined || Number.isNaN(x) ? '-' : Number(x).toFixed(d));

const runs = readdirSync(stageDir, { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .map((d) => parseRun(d.name))
  .filter(Boolean);

const groups = new Map();
for (const r of runs) {
  if (!groups.has(r.group)) groups.set(r.group, []);
  groups.get(r.group).push(r);
}

const cols = [
  ['p50 ms', 'p50'], ['p95 ms', 'p95'], ['p99 ms', 'p99'], ['노출 p95 ms', 'matchP95'],
  ['에러 %', 'errPct', 2], ['dropped', 'dropped', 0], ['중복 클릭', 'dup', 0],
  ['잠금 대기 max', 'lockMax', 0], ['잠금 대기 avg', 'lockAvg', 2],
  ['커넥션 대기 max', 'pendingMax', 0], ['CPU max', 'cpuMax', 2],
  ['SSE 이벤트/s', 'sseEvents'], ['SSE 기대/s', 'sseExpected'], ['SSE 지연 p95 ms', 'sseDelayP95', 0],
  ['SSE 실패', 'sseFailed', 0],
];

const lines = [];
lines.push(`# ${stageDir.split(/[\\/]/).filter(Boolean).pop()} — 중앙값`);
lines.push('');
lines.push('대표 응답 시간: A = 노출, B·D = 새 클릭(201). 값은 측정 구간(phase:measure)만.');
lines.push('');
lines.push(`| 시나리오 | 회수 | ${cols.map((c) => c[0]).join(' | ')} |`);
lines.push(`|---|---|${cols.map(() => '---').join('|')}|`);
for (const [group, rs] of [...groups.entries()].sort()) {
  const cells = cols.map(([, key, d = 1]) => fmt(median(rs.map((r) => r[key])), d));
  lines.push(`| ${group} | ${rs.length} | ${cells.join(' | ')} |`);
}
lines.push('');
lines.push('## 회차별 대표 p95 (ms)');
lines.push('');
for (const [group, rs] of [...groups.entries()].sort()) {
  const p95s = rs.sort((a, b) => String(a.run).localeCompare(String(b.run)))
    .map((r) => `run${r.run}=${fmt(r.p95 ?? r.sseDelayP95)}`);
  lines.push(`- ${group}: ${p95s.join(', ')}`);
}

const out = lines.join('\n') + '\n';
console.log(out);
writeFileSync(join(stageDir, 'summary.md'), out);
