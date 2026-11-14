---
id: 2026-11-12-campaign-spend-and-detail-api
title: 노출당 과금(2등+1원)과 캠페인 상세 지표·API 이름을 확정한다
zone: design
status: 확정
recorder: kmssss56
ai: Claude
recorded: 2026-11-12
discussed: 2026-11-12
refs: "#128"
files: 
summary: 노출당 차감은 min(내 입찰가, 다음 경쟁 입찰가+1원)로 하고, 필드는 #128 이름을 쓰며, 추이는 시간·일 단위, AI 요약은 Claude API, PDF는 OpenPDF를 쓴다
---

## 왜
프론트(PR #54·#55)와 백엔드(#128)가 필드 이름을 다르게 썼다. 차감 규칙이 정해져야 spent_total, CPC, CPM 값이 의미를 가진다.

## 결정
1. **차감액** = min(내 입찰가, 나보다 낮은 입찰가 중 최고 + 1원). 나보다 낮은 입찰가가 없으면 내 입찰가를 차감한다.
2. **필드 이름** (#128 기준, 프론트가 맞춘다): `totalBudget`, `bidPrice`, `spentTotal`, `remainingBudget`, `targetClicks`. 프론트 PR #54의 `budget`, `spentAmount`, `budgetUsageRate`는 바꾼다.
3. **추이 단위**: `granularity=hour|day`. 기본값 `day`. 주·월은 나중에 추가한다.
4. **AI 요약**: Claude API(`claude-sonnet-5-5`)를 서버에서 호출한다. API 키는 환경변수로만 받는다.
5. **PDF**: OpenPDF 라이브러리로 만든다. CSV는 표준 라이브러리로 만든다.
6. **CPC·CPM·CTR**은 백엔드가 계산해서 응답에 넣는다. 프론트는 계산하지 않는다.
7. **예산 저장 위치**: `campaigns` 테이블 컬럼 (이슈 #128, 채원님 안). ERD 의 `campaign_budget` 별도 테이블은 시간대 배분·환불이 필요해질 때 나중에 나눈다.
8. **입찰가 하한선**: 1원 이상 (별도 하한 없음). 실제 데이터를 보고 나중에 정한다.
9. **노출 선택 방식**: 입찰가가 높을수록 뽑힐 확률을 높인다 (확률 비례 방식). 누가 구현하는지는 담당이 정해지면 적는다.

## 대안
| 대안 | 기각 이유 |
|---|---|
| 프론트 이름(`budget`, `spentAmount`)에 맞춤 | 백엔드 이슈 #128이 이미 열려 있고 이름이 더 명확하다 |
| 노출당 고정 단가(예산 ÷ 목표 노출 수) | 입찰가 경쟁이 없어서 CPM이 항상 같은 일직선이 된다 |
| AI 요약을 규칙 기반 문장으로 | 요청한 "AI 요약"이 아니다 |

## 되돌릴 조건
- 경쟁 광고 데이터가 없어 차감 규칙이 실제로 돌아가지 않으면 이 규칙을 다시 본다.
- Claude API 비용이 예상보다 크면 요약 빈도를 줄이거나 캐시한다.
