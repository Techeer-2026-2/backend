// 시나리오 공통 설정. 부하 값은 P1 기준선에서 한 번 정한 뒤 바꾸지 않는다.
// 값을 바꿔야 하면 환경변수로 넘기고, 성능측정_기록.md 에 쓴 값을 함께 적는다.
import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const WARMUP = __ENV.WARMUP || '30s';
export const MEASURE = __ENV.MEASURE || '2m';
export const MEMBERS = Number(__ENV.MEMBERS || 1000); // seed.sql 의 회원 수 (user_id 1~MEMBERS)

// 200(배너 있음), 201(새 클릭), 204(배너 없음)는 정상으로 센다. 200 은 클릭의 중복 경로이기도 하다.
http.setResponseCallback(http.expectedStatuses(200, 201, 204));

export function randomMemberId() {
  return String(1 + Math.floor(Math.random() * MEMBERS));
}

/**
 * 같은 흐름을 워밍업 → 측정 두 구간으로 나눈다. 측정 구간만 phase:measure 태그가 붙는다.
 * constant-arrival-rate 라 응답이 느려져도 초당 요청 수가 유지된다(느려지면 VU 가 늘어난다).
 */
export function phases(flow, exec, rate) {
  const base = {
    executor: 'constant-arrival-rate',
    exec,
    rate,
    timeUnit: '1s',
    preAllocatedVUs: Number(__ENV.PRE_VUS || 50),
    maxVUs: Number(__ENV.MAX_VUS || 500),
  };
  return {
    [`${flow}_warmup`]: { ...base, duration: WARMUP, tags: { phase: 'warmup', flow } },
    [`${flow}_measure`]: { ...base, startTime: WARMUP, duration: MEASURE, tags: { phase: 'measure', flow } },
  };
}

/**
 * 요약에 측정 구간 값만 따로 나오게 하는 임계값. 판정용이 아니라 표시용이라 항상 통과한다.
 * (목표 수치가 정해지면 여기 대신 실제 임계값을 넣는다)
 */
const RATE_METRICS = ['http_req_failed', 'banner_filled', 'checks'];
const COUNTER_METRICS = ['click_duplicate', 'click_skipped', 'dropped_iterations', 'iterations', 'http_reqs'];

export function showOnly(...metrics) {
  const t = {};
  for (const m of metrics) {
    const name = m.split('{')[0];
    if (RATE_METRICS.includes(name)) t[m] = ['rate>=0'];
    else if (COUNTER_METRICS.includes(name)) t[m] = ['count>=0'];
    else t[m] = ['max>=0']; // Trend
  }
  return t;
}

export const SUMMARY_STATS = ['avg', 'p(50)', 'p(95)', 'p(99)', 'max', 'count'];
