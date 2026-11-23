// 시나리오 A. 노출: GET /api/v1/banners/match
//   k6 run -e RATE=100 loadtest/k6/a_impression.js
import { phases, showOnly, SUMMARY_STATS } from './lib/config.js';
export { impression } from './lib/flows.js';

const RATE = Number(__ENV.RATE || 600);

export const options = {
  scenarios: phases('impression', 'impression', RATE),
  summaryTrendStats: SUMMARY_STATS,
  thresholds: showOnly(
    'http_req_duration{phase:measure,name:match}',
    'http_req_failed{phase:measure}',
    'banner_filled{phase:measure}',
    'dropped_iterations{phase:measure}',
  ),
};
