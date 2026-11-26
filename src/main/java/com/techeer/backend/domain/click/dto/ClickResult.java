package com.techeer.backend.domain.click.dto;

/**
 * 클릭 처리 결과. sent=true 면 Kafka 로 바로 전송됨(201), false 면 실패 보관됨(202, 재전송 예정).
 */
public record ClickResult(ClickResponse click, boolean sent) {
}
