// 시나리오 C. 대시보드 SSE 구독자 N명을 띄운다. 외부 패키지 없이 Node 18+ 의 fetch 만 쓴다.
//
//   node loadtest/sse/sse-subscribers.mjs
//   SUBSCRIBERS=200 DURATION_SEC=210 CAMPAIGNS=1,2,3 node loadtest/sse/sse-subscribers.mjs
//
// 서버는 연결 직후 1번, 이후 INTERVAL_MS(서버 stats.stream.interval-ms, 기본 2000) 마다 stats 이벤트를 보낸다.
// 기록하는 값:
//   - 연결 성공/실패 수, 첫 이벤트까지 걸린 시간
//   - 이벤트 간격 - INTERVAL_MS = 지연 (서버가 늦게 보낼수록 커진다)
// 워밍업(WARMUP_SEC) 동안의 이벤트는 통계에서 뺀다.
import { writeFileSync } from 'node:fs';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const SUBSCRIBERS = Number(process.env.SUBSCRIBERS || 100);
const DURATION_SEC = Number(process.env.DURATION_SEC || 150); // 워밍업 30초 + 측정 2분
const WARMUP_SEC = Number(process.env.WARMUP_SEC || 30);
const INTERVAL_MS = Number(process.env.INTERVAL_MS || 2000);
const RAMP_MS = Number(process.env.RAMP_MS || 5000); // 구독자를 이 시간 동안 나눠서 연결한다
const CAMPAIGNS = (process.env.CAMPAIGNS || '1').split(',').map((s) => s.trim());
const EMAIL = process.env.ADVERTISER_EMAIL || 'lt-advertiser@loadtest.local';
const PASSWORD = process.env.ADVERTISER_PASSWORD || 'loadtest-pass-1234'; // seed.sql 의 로컬 전용 값
const OUT = process.env.OUT || '';

const startedAt = Date.now();
const measureFrom = startedAt + WARMUP_SEC * 1000;
const stopAt = startedAt + DURATION_SEC * 1000;

const stats = {
  connected: 0,
  connectFailed: 0,
  disconnectedEarly: 0,
  events: 0,
  firstEventMs: [],
  delayMs: [],
};

async function login() {
  const res = await fetch(`${BASE_URL}/api/v1/owners/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });
  if (!res.ok) throw new Error(`광고주 로그인 실패: ${res.status} ${await res.text()}`);
  return (await res.json()).accessToken;
}

async function subscribe(token, campaignId, controller) {
  const openedAt = Date.now();
  let res;
  try {
    res = await fetch(`${BASE_URL}/api/v1/campaigns/${campaignId}/stats/stream`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
      signal: controller.signal,
    });
  } catch (e) {
    if (!controller.signal.aborted) stats.connectFailed++;
    return;
  }
  if (!res.ok) {
    stats.connectFailed++;
    return;
  }
  stats.connected++;

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let last = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let idx;
      // SSE 이벤트는 빈 줄로 구분된다.
      while ((idx = buffer.search(/\r?\n\r?\n/)) >= 0) {
        const block = buffer.slice(0, idx);
        buffer = buffer.slice(idx).replace(/^\r?\n\r?\n/, '');
        if (!/^event:\s*stats/m.test(block)) continue;
        const now = Date.now();
        if (last === 0) {
          stats.firstEventMs.push(now - openedAt);
        } else if (now >= measureFrom) {
          stats.events++;
          stats.delayMs.push(Math.max(0, now - last - INTERVAL_MS));
        }
        last = now;
      }
    }
    if (Date.now() < stopAt - 1000) stats.disconnectedEarly++;
  } catch (e) {
    if (!controller.signal.aborted) stats.disconnectedEarly++;
  }
}

function percentile(values, p) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

function summarize(values) {
  return {
    count: values.length,
    p50: percentile(values, 50),
    p95: percentile(values, 95),
    p99: percentile(values, 99),
    max: values.length ? Math.max(...values) : null,
  };
}

async function main() {
  const token = await login();
  const controller = new AbortController();
  const tasks = [];
  for (let i = 0; i < SUBSCRIBERS; i++) {
    const campaignId = CAMPAIGNS[i % CAMPAIGNS.length];
    tasks.push(
      new Promise((r) => setTimeout(r, (RAMP_MS * i) / SUBSCRIBERS)).then(() =>
        subscribe(token, campaignId, controller),
      ),
    );
  }
  console.log(`SSE 구독자 ${SUBSCRIBERS}명, 캠페인 [${CAMPAIGNS}], ${DURATION_SEC}초 (워밍업 ${WARMUP_SEC}초)`);

  await new Promise((r) => setTimeout(r, stopAt - Date.now()));
  controller.abort();
  await Promise.allSettled(tasks);

  const measuredSec = Math.max(1, DURATION_SEC - WARMUP_SEC);
  const result = {
    subscribers: SUBSCRIBERS,
    campaigns: CAMPAIGNS,
    intervalMs: INTERVAL_MS,
    connected: stats.connected,
    connectFailed: stats.connectFailed,
    disconnectedEarly: stats.disconnectedEarly,
    eventsPerSec: Number((stats.events / measuredSec).toFixed(1)),
    expectedEventsPerSec: Number(((SUBSCRIBERS * 1000) / INTERVAL_MS).toFixed(1)),
    firstEventMs: summarize(stats.firstEventMs),
    eventDelayMs: summarize(stats.delayMs),
  };
  console.log(JSON.stringify(result, null, 2));
  if (OUT) writeFileSync(OUT, JSON.stringify(result, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
