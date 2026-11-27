// 시나리오 D. 혼합: A(노출만) + B(노출→클릭) 를 동시에. C(SSE 구독자)는 run-scenario.sh 가 함께 띄운다.
//   k6 run -e RATE_A=300 -e RATE_B=150 loadtest/k6/d_mixed.js
import { phases, showOnly, SUMMARY_STATS } from './lib/config.js';
export { impression, impressionThenClick } from './lib/flows.js';

const RATE_A = Number(__ENV.RATE_A || 300);
const RATE_B = Number(__ENV.RATE_B || 150);

export const options = {
  scenarios: {
    ...phases('impression', 'impression', RATE_A),
    ...phases('click', 'impressionThenClick', RATE_B),
  },
  summaryTrendStats: SUMMARY_STATS,
  thresholds: showOnly(
    'http_req_duration{phase:measure,flow:impression,name:match}',
    'http_req_duration{phase:measure,flow:click,name:match}',
    'click_new_duration{phase:measure}',
    'http_req_failed{phase:measure}',
    'banner_filled{phase:measure}',
    'click_duplicate{phase:measure}',
    'dropped_iterations{phase:measure}',
  ),
};
