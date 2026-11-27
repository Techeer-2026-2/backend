---
id: 2026-11-26-ad-archive-offsets
title: 광고 이벤트 저장 배치와 Kafka 오프셋의 경계
zone: dev
status: 제안
recorder: coder072
ai: Codex
recorded: 2026-11-26
discussed: 2026-11-26
refs: -
files: src/main/java/com/techeer/backend/domain/recompute/
summary: Kafka poll 스레드가 배치 저장과 오프셋 커밋을 순서대로 실행한다
---

## 왜
- 1분 동안 새 메시지가 없어도 원본 파일을 닫아야 한다. 타이머 스레드에서 오프셋을 따로 처리하면 파티션의 앞선 메시지가 저장되기 전에 커밋할 수 있다.

## 결정
- Spring Kafka가 가져오는 Kafka 클라이언트에서 poll 스레드가 오래된 배치를 확인한다. 해당 배치의 S3 파일 및 기록을 모두 저장한 후 파티션별 오프셋을 커밋한다. 실패 후 재처리된 원본의 중복은 재계산에서 제거한다.
- 재계산과 대조는 PostgreSQL advisory lock을 공유한다. 별도 ClickHouse JDBC 연결로 stage를 교체하며, 실행 이력은 PostgreSQL에 즉시 기록한다.
- B가 작성하는 SQL은 `recompute.sql-location`의 `archive-counts.sql`, `daily-count.sql`, `replace-partition.sql`로 읽는다. 날짜 리터럴 `{{date}}`, 숫자 파티션 `{{partition}}`, S3 URL 리터럴 `{{s3_url}}`, 테이블 이름 `{{facts}}`를 지원한다. 변수 이름 첫 글자 뒤에 숫자를 허용해 `{{s3_url}}`이 SQL에 그대로 남는 문제를 해결한다. 첫 쿼리는 `file_count`, `line_count`, `unique_count`를 한 행으로 반환한다. 둘째 쿼리는 건수 하나를 반환한다. 교체 SQL은 세미콜론으로 나눈 문장 순서대로 실행한다.

## 로컬 실행
- PostgreSQL에 `docs/sql/ad-pipeline/004_recompute_log.sql`을 적용한다. Kafka와 S3 또는 MinIO를 연결한 뒤 원본 저장은 `raw.enabled=true`, 클릭 사본은 `click.copy.enabled=true`로 각각 켠다. MinIO 주소는 `raw.s3.endpoint`에 넣고 ClickHouse에서 보는 주소가 다르면 `recompute.s3-endpoint`에 지정한다.
- ClickHouse DDL과 SQL 리소스를 연결한 뒤 `recompute.enabled=true`로 예약 작업을 켠다. 수동 실행은 `./gradlew bootRun --args='--spring.profiles.active=local,recompute-command --recompute.enabled=true --recompute.from=2026-11-20 --recompute.to=2026-11-21'`을 사용한다.
