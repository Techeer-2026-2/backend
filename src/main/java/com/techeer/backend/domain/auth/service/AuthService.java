package com.techeer.backend.domain.auth.service;

import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.auth.dto.LoginRequest;
import com.techeer.backend.domain.auth.dto.LogoutRequest;
import com.techeer.backend.domain.auth.dto.RefreshRequest;
import com.techeer.backend.domain.auth.dto.TokenResponse;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenHasher;
import com.techeer.backend.domain.auth.jwt.TokenType;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AdvertiserRepository advertiserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 없는 이메일로 로그인했을 때도 비밀번호 비교를 한 번 실행하기 위한 가짜 해시.
     * 비교를 건너뛰면 "없는 이메일"이 "틀린 비밀번호"보다 눈에 띄게 빨리 응답해서 가입 여부를 알아낼 수 있다.
     */
    private final String dummyPasswordHash;

    public AuthService(
            AdvertiserRepository advertiserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.advertiserRepository = advertiserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 이메일·비밀번호를 확인하고 access/refresh token 을 발급한다.
     *
     * <p>이메일이 없을 때와 비밀번호가 틀렸을 때, 탈퇴한 계정일 때 모두 같은 INVALID_CREDENTIALS 로 응답한다.
     * 한 번 로그인할 때마다 refresh token 해시를 새 값으로 덮어쓰므로 광고주당 로그인 세션은 하나만 유지된다
     * (다른 기기에서 로그인하면 이전 기기의 refresh token 은 쓸 수 없게 된다).
     *
     * @param request 이메일과 비밀번호
     * @return 발급된 토큰
     * @throws BusinessException 로그인 정보가 맞지 않는 경우
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        Advertiser advertiser = advertiserRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);

        String hashToCompare = advertiser == null ? dummyPasswordHash : advertiser.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCompare);
        if (advertiser == null || !passwordMatches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(advertiser.getUserId());
        String refreshToken = jwtTokenProvider.createRefreshToken(advertiser.getUserId());
        advertiser.changeRefreshTokenHash(TokenHasher.sha256Hex(refreshToken));
        return TokenResponse.of(accessToken, refreshToken, jwtTokenProvider.accessTokenExpiresInSeconds());
    }

    /**
     * refresh token 으로 새 access token 을 발급한다. 이때 refresh token 도 새것으로 교체(회전)하고 이전 것은 폐기한다.
     *
     * <p>서명·만료가 맞아도 DB 에 저장된 해시와 다르면 거절한다. 이미 한 번 쓴 토큰, 로그아웃한 토큰,
     * 다른 기기에서 다시 로그인하기 전의 토큰, 탈퇴한 계정의 토큰이 여기에 걸린다.
     * 같은 토큰으로 동시에 여러 번 요청해도 DB 의 조건부 UPDATE 덕분에 한 번만 성공한다.
     *
     * @param request 기존 refresh token
     * @return 새로 발급한 access/refresh token
     * @throws BusinessException 토큰이 유효하지 않거나 이미 폐기된 경우
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Long advertiserId = jwtTokenProvider.parseAdvertiserId(request.refreshToken(), TokenType.REFRESH);

        String accessToken = jwtTokenProvider.createAccessToken(advertiserId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(advertiserId);

        int rotated = advertiserRepository.rotateRefreshTokenHash(
                advertiserId,
                TokenHasher.sha256Hex(request.refreshToken()),
                TokenHasher.sha256Hex(newRefreshToken));
        if (rotated == 0) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return TokenResponse.of(accessToken, newRefreshToken, jwtTokenProvider.accessTokenExpiresInSeconds());
    }

    /**
     * 로그아웃한다. 저장된 refresh token 해시를 지워서 이 토큰으로는 더 이상 갱신할 수 없게 한다.
     *
     * <p>이미 로그아웃했거나 이미 교체된 옛 토큰이어도 에러 없이 성공으로 처리한다(여러 번 요청해도 결과가 같다).
     * 옛 토큰으로는 지금 쓰는 새 세션을 끊지 못한다. 이미 만들어 준 access token 은 만료될 때까지(30분) 유효하다.
     * 그 사이를 막으려면 access token 블랙리스트가 필요한데 Redis 가 없어 이번 범위에서는 하지 않는다.
     *
     * @param request 로그아웃할 refresh token
     * @throws BusinessException 서명·만료·형식이 올바르지 않은 토큰인 경우
     */
    @Transactional
    public void logout(LogoutRequest request) {
        Long advertiserId = jwtTokenProvider.parseAdvertiserId(request.refreshToken(), TokenType.REFRESH);
        advertiserRepository.clearRefreshTokenHash(advertiserId, TokenHasher.sha256Hex(request.refreshToken()));
    }
}
