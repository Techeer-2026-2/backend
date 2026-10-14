package com.techeer.backend.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "페이지 단위 목록 응답")
public record PageResponse<T>(
        @Schema(description = "이번 페이지의 항목") List<T> content,
        @Schema(description = "현재 페이지 번호(0부터 시작)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 항목 수", example = "57") long totalElements,
        @Schema(description = "전체 페이지 수", example = "3") int totalPages) {

    /**
     * Spring Data 의 Page 를 응답 모양으로 바꾼다. Page 를 그대로 반환하면 응답 구조가 라이브러리 내부 구현에 묶인다.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
