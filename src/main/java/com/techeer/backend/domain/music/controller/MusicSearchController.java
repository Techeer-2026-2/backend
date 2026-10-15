package com.techeer.backend.domain.music.controller;

import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.domain.music.service.MusicSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Music", description = "음악 검색")
public class MusicSearchController {

    private final MusicSearchService musicSearchService;

    @Operation(summary = "음악 검색", description = "키워드(최대 50자)로 iTunes에서 곡을 검색한다.")
    @GetMapping("/music/search")
    public List<TrackCandidateResponse> search(@RequestParam @NotBlank @Size(max = 50) String term) {
        return musicSearchService.search(term);
    }
}
