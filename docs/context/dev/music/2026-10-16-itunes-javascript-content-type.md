---
id: 2026-10-16-itunes-javascript-content-type
title: iTunes Search 응답이 text/javascript라서 JSON 컨버터를 추가했다
zone: dev
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#92"
files: domain/music/config/ItunesClientConfig.java
summary: /music/search가 실제로는 502였던 원인은 iTunes가 JSON을 text/javascript로 보내서이고, RestClient에 해당 컨버터를 추가해 고쳤다
---

## 왜
`GET /api/v1/music/search`가 실제 iTunes를 호출하면 502가 났다. 응답 본문은 JSON인데 `Content-Type`이 `text/javascript`라서 Spring `RestClient`의 기본 컨버터가 읽지 못했다 (`no suitable HttpMessageConverter`). 기존 테스트는 `application/json`으로 모의해서 잡지 못했다.

## 결정
- `ItunesClientConfig`가 `MappingJackson2HttpMessageConverter`를 `text/javascript`와 `application/json` 둘 다 지원하게 만들어 `RestClient`에 등록한다
- 컨버터를 빼면 `text/javascript`로 응답하는 모의 테스트가 실패하는 것을 확인했다

## 대안
원인이 명확해서 다른 후보는 비교하지 않았다.

## 되돌릴 조건
iTunes가 `application/json`으로 응답을 바꾸면 이 설정은 불필요하지만 해롭지 않다.
