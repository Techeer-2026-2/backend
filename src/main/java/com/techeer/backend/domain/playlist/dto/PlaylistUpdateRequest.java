package com.techeer.backend.domain.playlist.dto;

/**
 * PATCH 요청이라 모든 필드가 선택사항이다. null인 필드는 기존 값을 그대로 유지한다.
 */
public record PlaylistUpdateRequest(String name, String description, Boolean isPublic) {
}
