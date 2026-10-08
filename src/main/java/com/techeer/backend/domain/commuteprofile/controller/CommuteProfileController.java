package com.techeer.backend.domain.commuteprofile.controller;

import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileCreateRequest;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileResponse;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileUpdateRequest;
import com.techeer.backend.domain.commuteprofile.service.CommuteProfileService;
import com.techeer.backend.global.resolver.MemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commute-profiles")
@RequiredArgsConstructor
@Tag(name = "CommuteProfile", description = "통근 프로필")
public class CommuteProfileController {

    private final CommuteProfileService commuteProfileService;

    @Operation(summary = "통근 프로필 등록")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CommuteProfileResponse create(@MemberId Long memberId, @RequestBody CommuteProfileCreateRequest request) {
        return commuteProfileService.create(memberId, request);
    }

    @Operation(summary = "통근 프로필 목록 조회")
    @GetMapping
    public List<CommuteProfileResponse> findAll(@MemberId Long memberId) {
        return commuteProfileService.findAll(memberId);
    }

    @Operation(summary = "통근 프로필 상세 조회")
    @GetMapping("/{profileId}")
    public CommuteProfileResponse findDetail(@MemberId Long memberId, @PathVariable Long profileId) {
        return commuteProfileService.findDetail(memberId, profileId);
    }

    @Operation(summary = "통근 프로필 수정")
    @PutMapping("/{profileId}")
    public CommuteProfileResponse update(
            @MemberId Long memberId,
            @PathVariable Long profileId,
            @RequestBody CommuteProfileUpdateRequest request) {
        return commuteProfileService.update(memberId, profileId, request);
    }

    @Operation(summary = "통근 프로필 삭제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{profileId}")
    public void delete(@MemberId Long memberId, @PathVariable Long profileId) {
        commuteProfileService.delete(memberId, profileId);
    }
}
