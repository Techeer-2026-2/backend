package com.techeer.backend.domain.click.controller;

import com.techeer.backend.domain.click.dto.ClickRequest;
import com.techeer.backend.domain.click.dto.ClickResponse;
import com.techeer.backend.domain.click.dto.ClickResult;
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
            description = "배너 클릭을 기록하고 캠페인 클릭 수를 1 올린다. "
                    + "같은 notificationId 로 다시 요청하면 새로 기록하지 않고 기존 클릭을 돌려준다(멱등).")
    @ApiResponse(responseCode = "201", description = "새로 기록됨")
    @ApiResponse(responseCode = "200", description = "이미 기록된 클릭 (중복 요청)")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않는 notificationId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/clicks")
    public ResponseEntity<ClickResponse> recordClick(@Valid @RequestBody ClickRequest request) {
        ClickResult result = clickService.recordClick(request.notificationId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.click());
    }
}
