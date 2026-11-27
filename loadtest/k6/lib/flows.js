import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, randomMemberId } from './config.js';

export const bannerFilled = new Rate('banner_filled');          // 노출 응답 중 배너가 있는(200) 비율
export const clickNewDuration = new Trend('click_new_duration', true); // 새 클릭(201) 응답 시간
export const clickDuplicate = new Counter('click_duplicate');  // 200(중복) — 1:1 이면 0 이어야 한다
export const clickSkipped = new Counter('click_skipped');      // 노출이 204 라 클릭을 못 한 횟수

/** 시나리오 A: 노출. 회원은 X-Member-Id 로 식별한다(로컬 legacy-member-header=true). */
export function impression() {
  const res = http.get(`${BASE_URL}/api/v1/banners/match`, {
    headers: { 'X-Member-Id': randomMemberId() },
    tags: { name: 'match' },
  });
  check(res, { 'match 200/204': (r) => r.status === 200 || r.status === 204 });
  bannerFilled.add(res.status === 200);
  return res;
}

/** 시나리오 B: 노출 → 받은 notificationId 로 클릭 1회 (같은 ID 를 다시 쓰지 않는다). */
export function impressionThenClick() {
  const match = impression();
  if (match.status !== 200) {
    clickSkipped.add(1);
    return;
  }
  const notificationId = match.json('notificationId');
  const res = http.post(`${BASE_URL}/api/v1/clicks`, JSON.stringify({ notificationId }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'click' },
  });
  check(res, { 'click 201': (r) => r.status === 201 });
  if (res.status === 201) clickNewDuration.add(res.timings.duration);
  if (res.status === 200) clickDuplicate.add(1);
}
