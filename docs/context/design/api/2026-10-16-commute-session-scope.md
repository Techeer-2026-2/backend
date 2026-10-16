---
id: 2026-10-16-commute-session-scope
title: 통근 세션은 시작·이벤트·종료·취소·요약만 하고 정차역·경로 좌표는 이번에 뺀다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#105"
files: domain/commutesession/service/CommuteSessionService.java, domain/commutesession/entity/CommuteSession.java
summary: API 명세서의 통근 세션 6개를 구현하되 남은 시간·진행률은 프론트가 시작 시각과 예상 소요시간으로 계산하게 하고, 랭킹용 /playback 과는 따로 둔다
---

## 왜
프론트 지도 화면에는 "통근 중 · 현재: 선릉역 · 남은 시간 16분(50%)" 표시가 이미 있다. 서버에는 통근 세션이 없었고, 경로 좌표·정차역은 응답으로 주지 않는다. 기능명세서에는 통근 세션 항목이 없고 API 명세서에만 있다.

## 결정
- 시작할 때 프로필 경로의 평균 소요시간을 `expectedDurationMinutes` 로 복사해 둔다. 남은 시간·진행률은 프론트가 `startedAt` 으로 계산한다. "현재 역" 표시에 필요한 정차역·경로 좌표는 이번 범위에서 제외한다
- 회원당 진행 중 통근 1개는 `active_member_id` 칼럼에 unique 를 걸어 DB 가 보장한다. 진행 중일 때만 회원 ID 를 넣고 끝나면 NULL 로 비운다 (NULL 끼리는 unique 에 걸리지 않아 끝난 기록은 쌓인다)
- 종료·취소는 "진행 중일 때만" 바꾸는 조건부 UPDATE 한 문장이라 동시에 와도 한쪽만 성공한다
- 재생 이벤트는 앱이 만든 `eventId`(UUID)로 중복을 막는다. 같은 내용이면 200(created=false), 내용이 다르거나 다른 통근의 이벤트면 409. 곡은 `itunesTrackId` 로 가리킨다
- 랭킹용 `/playback/sessions`·`/playback/events`(곡 하나의 재생 기록)와는 따로 둔다. 통근 요약은 곡 정보를 모르는 곡도 이름 없이 보여 준다

## 대안
| 대안 | 기각 이유 |
|---|---|
| 정차역·경로 좌표까지 응답 | 프로필 저장 구조와 TMAP 응답 가공까지 범위가 커진다 |
| `/playback` 에 통근 정보를 합침 | 랭킹 집계용 구조(곡 단위·위치 검증)와 목적이 달라 서로 영향을 준다 |

## 한계
통근 세션을 팀이 실제로 쓸지는 확정되지 않았다(기능명세서에 없음). 끝난 통근에는 이벤트를 새로 보낼 수 없어, 도착 직전 이벤트가 늦게 도착하면 버려진다. 프론트에 플레이어가 없어 재생 이벤트는 아직 보낼 곳이 없다.

## 되돌릴 조건
팀이 통근 세션을 쓰지 않기로 하면 제거한다. 정차역 표시가 필요해지면 경로 상세를 응답에 담는 이슈를 따로 만든다.
