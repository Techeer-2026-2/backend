---
id: 2026-10-16-member-token-with-header-fallback
title: 음악 앱 회원 식별은 로그인 토큰을 받되 X-Member-Id 를 설정으로 끌 수 있게 병행한다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#99, #63"
files: global/resolver/MemberIdArgumentResolver.java, domain/user/controller/UserController.java
summary: "@MemberId 는 Bearer 토큰이 있으면 토큰으로, 없을 때만 설정(auth.legacy-member-header)이 켜진 동안 X-Member-Id 로 식별하고, 내 정보 API 는 닉네임만 수정한다"
---

## 왜
X-Member-Id 는 헤더 값만 바꾸면 다른 회원으로 행세할 수 있다(#63). 로그인 API 는 있지만 프론트에는 로그인 화면이 없고 `X-Member-Id: 1` 을 고정으로 보내서, 리졸버를 바로 토큰 전용으로 바꾸면 연동된 화면이 모두 401 이 된다. 프론트 연동은 나중에 하기로 했다.

## 결정
- 토큰이 있으면 토큰으로만 식별한다. 토큰이 잘못됐거나 광고주 토큰이면 헤더가 있어도 401 이다 (헤더로 대신하면 우회가 된다)
- 토큰이 없을 때만 `auth.legacy-member-header`(환경변수 `AUTH_LEGACY_MEMBER_HEADER`, 기본 true)가 켜진 동안 X-Member-Id 를 쓴다
- 프론트가 로그인 토큰으로 전환하면 운영에서 false 로 바꿔 임시 헤더를 끈다. 끄는 시점은 프론트 전환 일정에 달려 있고 정해지지 않았다
- `GET·PATCH /api/v1/me` 는 닉네임만 수정한다. 프로필 이미지는 `users` 테이블에 칼럼이 없어 제외했다 (Spotify 연결 상태는 iTunes 대체로 해당 없음)

## 대안
| 대안 | 기각 이유 |
|---|---|
| 지금 토큰 전용으로 교체 | 프론트 화면이 모두 401 이 된다 |
| 프론트 로그인 연동이 끝난 뒤에 교체 (#63 에 적힌 원래 순서) | 그때까지 토큰으로 못 쓰고, 교체할 때 한 번에 깨질 위험이 있다 |

## 한계
임시 헤더를 끄기 전까지 X-Member-Id 로 다른 회원 행세를 하는 구멍은 그대로다.

## 되돌릴 조건
프론트가 로그인 토큰으로 전환하면 운영 설정을 false 로 바꾸고, 안정되면 헤더 분기 코드를 지운다.
