# coder072 광고 집계 고도화 작업 요약

## 작업 흐름

1. #202의 SQL 004를 PostgreSQL에 적용해 `recompute_log`와 `raw_archive_batch`를 만든다.
2. #203의 S3 저장 컨슈머는 Kafka의 클릭·노출 메시지를 받아 토픽과 이벤트 시간별로 JSON Lines 파일을 만든다. S3 업로드 및 배치 기록이 끝나면 Kafka 오프셋을 커밋한다.
3. #204의 재계산은 ClickHouse 파티션을 날짜별로 교체하고, 중복 제거 수와 실행 상태를 기록한다. 스케줄은 매일 03:00 KST에 최근 이틀을 처리한다. SQL 템플릿의 `{{s3_url}}`은 변수 이름에 숫자를 허용하는 규칙으로 치환한다.
4. #205의 대조는 매일 03:30 KST에 S3 원본과 ClickHouse 확정 건수를 비교하고 PostgreSQL 노출 건수도 확인한다. 차이가 있으면 이유와 함께 `recompute_log.reconcile_ok = false`를 기록한다.
5. #206의 클릭 사본 컨슈머는 별도 Kafka 그룹에서 클릭을 읽어 `click_events`에 배치로 저장한다. 동일한 `notification_id`는 한 행만 남는다.

## 실행

- Kafka 발행과 S3 설정을 연결한 뒤, 원본 저장은 `raw.enabled=true`, 클릭 사본은 `click.copy.enabled=true`로 각각 활성화한다.
- ClickHouse DDL과 운영 SQL 템플릿 연결은 담당 B의 #195·#200에서 진행한다. 연결 후 `recompute.enabled=true`로 예약 작업을 시작한다.
- 수동 실행은 아래처럼 날짜 범위의 시작일과 종료일을 모두 지정한다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local,recompute-command --recompute.enabled=true --recompute.from=2026-11-20 --recompute.to=2026-11-21'
```

## 검증

- 이전 클라우드 검증에서 실제 ClickHouse를 사용해 중복 제거, 같은 날짜를 두 번 재계산했을 때 결과 일치, 원본 누락 시 불일치 기록을 확인했다.
- 이번 로컬 검증은 최신 `develop` (`5cbc1c8`)에서 시작했다. PostgreSQL 16에 SQL 004를 두 번 적용해 두 테이블과 인덱스를 확인했다. Kafka 테스트에서는 배치 저장 후 오프셋 커밋 전에 실패했을 때 재처리되는 것을 확인했다.
- ClickHouse 25.8, MinIO, PostgreSQL 16을 띄운 통합 테스트에서 `{{s3_url}}`이 들어 있는 테스트 SQL을 실행했다. 같은 날짜를 두 번 처리한 결과가 2건으로 일치했고 각 실행에서 중복 클릭 1건을 제거했다. S3 원본 파일을 지운 뒤 대조 실패가 기록됐으며, PostgreSQL 클릭 사본에는 같은 클릭이 한 행만 남았다.
- 최종 브랜치의 `./gradlew test`는 423개 통과(실패 0, 건너뜀 0)했다. `bash scripts/check-docs.sh`도 통과했다.
