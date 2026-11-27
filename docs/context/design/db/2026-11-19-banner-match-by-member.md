---
id: 2026-11-19-banner-match-by-member
title: 배너 매칭을 target_users 대신 로그인한 회원(users)의 연령대·성별로 한다
zone: design
status: 제안
recorder: KimKyuBo0411
ai: Claude
recorded: 2026-11-19
discussed: 2026-11-19
refs: -
files: src/main/java/com/techeer/backend/domain/banner/service/BannerService.java
summary: target_users 를 없애고 users 에 age_group·gender 를 추가해 /banners/match 는 @MemberId 회원으로 매칭하며 노출·클릭의 target_user_id 컬럼에는 회원 ID 를 담는다
---

## 왜
target_users 는 생성 API 가 없고 users 와 연결되지 않아, 프론트가 targetUserId 로 보낼 번호가 없었다.
아무 번호나 보낼 수 있어 노출·클릭(광고비 차감)을 조작할 수도 있었다.

## 결정
- users 에 age_group(10s~50s)·gender(male, female) nullable 컬럼 추가. 회원가입·PATCH /api/v1/me 에서 선택 입력
- GET /banners/match 는 targetUserId 파라미터를 없애고 @MemberId(토큰 또는 X-Member-Id)로 회원을 찾는다
- 연령대가 없는 회원은 204. 탈퇴·없는 회원은 404 USER_NOT_FOUND
- ad_impressions·click_events 의 target_user_id 컬럼 이름은 그대로 두고 회원 ID 를 담는다.
  이름을 바꾸면 ddl-auto: update 인 DB 에 옛 NOT NULL 컬럼이 남아 INSERT 가 깨진다
- TargetUser 엔티티는 지운다. DB 의 target_users 테이블은 남아도 쓰이지 않는다
- 운영(ddl-auto: validate)은 배포 전에 수동으로 반영한다:
  `ALTER TABLE users ADD COLUMN age_group varchar(16), ADD COLUMN gender varchar(8);`

## 대안
| 대안 | 기각 이유 |
|---|---|
| target_users 에 user_id 를 연결 | 같은 사람 정보가 두 테이블에 나뉘고 가입 시 두 곳에 써야 한다 |
| 앱이 익명 target_user 를 발급받아 저장 | 번호 조작을 막을 수 없고 회원과 연결되지 않는다 |

## 되돌릴 조건
비회원에게도 배너를 보여 줘야 하면 익명 식별 방식을 다시 검토한다.
