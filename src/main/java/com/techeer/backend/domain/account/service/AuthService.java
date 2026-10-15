package com.techeer.backend.domain.account.service;

import com.techeer.backend.domain.account.dto.AuthDtos.LoginRequest;
import com.techeer.backend.domain.account.dto.AuthDtos.LoginResponse;
import com.techeer.backend.domain.account.dto.AuthDtos.SignUpRequest;
import com.techeer.backend.domain.account.dto.AuthDtos.UserResponse;
import com.techeer.backend.domain.account.entity.MusicUser;
import com.techeer.backend.domain.account.repository.MusicUserRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MusicUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    @Value("${auth.jwt.ttl-seconds}")
    private long tokenTtlSeconds;

    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        String email = normalizeEmail(request.email());
        String displayName = request.displayName().strip();
        if (displayName.length() < 2 || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (users.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        try {
            MusicUser saved = users.saveAndFlush(new MusicUser(email,
                    passwordEncoder.encode(request.password()), displayName));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            // 동시에 같은 이메일을 가입하는 요청에서도 DB의 UNIQUE 제약이 최종 보장한다.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        MusicUser user = users.findByEmailAndDeletedAtIsNull(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("commute-playlist")
                .subject(user.getUserId().toString())
                .audience(List.of("music-app"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(tokenTtlSeconds))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", tokenTtlSeconds, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String subject) {
        try {
            return users.findByUserIdAndDeletedAtIsNull(Long.parseLong(subject))
                    .map(UserResponse::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
