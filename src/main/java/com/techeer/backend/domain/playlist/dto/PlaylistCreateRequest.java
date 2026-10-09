package com.techeer.backend.domain.playlist.dto;

import jakarta.validation.constraints.NotBlank;

public record PlaylistCreateRequest(@NotBlank String name) {
}
