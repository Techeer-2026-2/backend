package com.techeer.backend.domain.kakao.controller;

import com.techeer.backend.domain.kakao.dto.PlaceSearchResponse;
import com.techeer.backend.domain.kakao.service.KakaoPlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Kakao", description = "장소 검색")
public class KakaoPlaceController {

    private final KakaoPlaceService kakaoPlaceService;

    @Operation(summary = "장소 검색", description = "키워드로 카카오 장소를 검색한다.")
    @GetMapping("/places/search")
    public List<PlaceSearchResponse> search(@RequestParam @NotBlank String keyword) {
        return kakaoPlaceService.search(keyword);
    }
}
