package com.techeer.backend.domain.account.dto;

import com.techeer.backend.domain.account.entity.MusicUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record SignUpRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 2, max = 40) String displayName) {
        public SignUpRequest {
            if (email != null) email = email.strip();
            if (displayName != null) displayName = displayName.strip();
        }
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {
        public LoginRequest {
            if (email != null) email = email.strip();
        }
    }

    public record UserResponse(Long userId, String email, String displayName) {
        public static UserResponse from(MusicUser user) {
            return new UserResponse(user.getUserId(), user.getEmail(), user.getDisplayName());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }

    public record Data<T>(T data) {
    }
}
