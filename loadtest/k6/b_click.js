// 시나리오 B. 클릭: 노출 1회 → 클릭 1회. RATE 는 초당 (노출+클릭) 묶음 수다.
//   k6 run -e RATE=50 loadtest/k6/b_click.js
import { phases, showOnly, SUMMARY_STATS } from './lib/config.js';
export { impressionThenClick } from './lib/flows.js';

const RATE = Number(__ENV.RATE || 300);

export const options = {
  scenarios: phases('click', 'impressionThenClick', RATE),
  summaryTrendStats: SUMMARY_STATS,
  thresholds: showOnly(
    'click_new_duration{phase:measure}',
    'http_req_duration{phase:measure,name:click}',
    'http_req_duration{phase:measure,name:match}',
    'http_req_failed{phase:measure}',
    'click_duplicate{phase:measure}',
    'dropped_iterations{phase:measure}',
  ),
};
