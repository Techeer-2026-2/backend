package com.techeer.backend.domain.click.dto;

/**
 * 클릭 기록 결과. created 가 false 면 이미 기록된 클릭에 대한 재요청이다.
 */
public record ClickResult(ClickResponse click, boolean created) {
}
