---
id: 2026-10-29-campaign-banner-image-s3
title: 캠페인 등록·수정 API 가 배너 이미지 파일을 직접 받아 S3 에 저장한다
zone: design
status: 제안
recorder: chae030
ai: Claude
recorded: 2026-10-29
discussed: 2026-10-28~2026-10-29
refs: "#109"
files: domain/campaign/, global/storage/, global/config/MultipartJsonPartConverter.java
summary: 캠페인 등록·수정을 multipart(request JSON + image 파일)로 바꾸고 이미지는 S3 에 저장, 운영 권한은 EC2 IAM 역할로 받는다
---

## 왜
프론트 캠페인 등록 화면이 이미지 URL 입력에서 파일 업로드로 바뀌었다. 지금까지 서버는 광고주가 입력한 외부 URL 문자열만 저장해서 파일을 보관할 곳이 없었다.

## 결정
- `POST /campaigns`(image 필수), `PATCH /campaigns/{id}`(request·image 둘 다 선택)를 multipart 로 바꾼다. 요청 DTO 의 `imageUrl` 은 뺀다
- 이미지는 S3 `campaign-banners/{UUID}.{확장자}` 에 저장하고 응답의 `imageUrl` 에 공개 URL 을 준다
- png·jpg·webp 만, 최대 5MB (`spring.servlet.multipart`). 형식은 브라우저가 보낸 Content-Type 으로 판단한다
- 업로드 후 트랜잭션이 롤백되면 올린 이미지를 지우고, 이미지 교체가 커밋되면 이전 이미지를 지운다. 외부 URL(기존 캠페인)은 지우지 않는다
- 운영은 EC2 IAM 역할로 권한을 받는다(키 없음). 로컬만 액세스 키를 쓴다
- JSON 파트를 Content-Type 없이 보내도 읽도록 octet-stream 읽기 전용 변환기를 MVC 에 추가했다

## 한계
- S3 업로드가 DB 트랜잭션 안에서 일어나 업로드하는 동안 DB 연결을 잡고 있다. 배너 등록은 드물어 받아들였다
- 형식 검사는 Content-Type 만 본다. 파일 내용(매직 바이트)은 확인하지 않는다

## 대안
| 대안 | 기각 이유 |
|---|---|
| 업로드 API(`POST /images`)를 따로 두고 캠페인 API 는 imageUrl 유지 | 프론트가 등록 요청 한 번에 보내는 쪽을 원했다 |
| EC2 디스크 + nginx 서빙 | 서버를 바꾸거나 볼륨을 지우면 이미지가 사라진다 |
| DB(bytea)에 저장 | 노출마다 DB 부하, 이미지 응답 API 추가 필요 |
| 운영에도 액세스 키 | 유출 위험. IAM 역할이면 키를 다루지 않아도 된다 |

## 되돌릴 조건
배너 등록이 잦아져 DB 연결 대기가 보이면 업로드를 트랜잭션 밖으로 뺀다. CDN 이 필요해지면 CloudFront 를 붙이고 `S3_PUBLIC_BASE_URL` 만 바꾼다.
