package com.techeer.backend.domain.click.controller;

import com.techeer.backend.domain.click.dto.ClickRequest;
import com.techeer.backend.domain.click.dto.ClickResponse;
import com.techeer.backend.domain.click.service.ClickService;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Click", description = "배너 클릭 기록")
public class ClickController {

    private final ClickService clickService;

    @Operation(
            summary = "클릭 이벤트 기록",
            description = "배너 클릭을 Kafka 로 보낸다. DB 에는 쓰지 않는다. "
                    + "같은 notificationId 로 여러 번 와도 중복은 재계산에서 정리되므로 응답은 항상 201 이다.")
    @ApiResponse(responseCode = "201", description = "클릭이 전송됨 (중복이어도 201)")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않는 notificationId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Kafka 전송 실패 (다시 시도)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/clicks")
    public ResponseEntity<ClickResponse> recordClick(@Valid @RequestBody ClickRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clickService.recordClick(request.notificationId()));
    }
}
