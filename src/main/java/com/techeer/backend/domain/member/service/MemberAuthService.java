package com.techeer.backend.domain.member.service;

import com.techeer.backend.domain.auth.dto.LogoutRequest;
import com.techeer.backend.domain.auth.dto.RefreshRequest;
import com.techeer.backend.domain.auth.dto.TokenResponse;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenHasher;
import com.techeer.backend.domain.auth.jwt.TokenType;
import com.techeer.backend.domain.member.dto.MemberLoginRequest;
import com.techeer.backend.domain.member.dto.MemberSignupRequest;
import com.techeer.backend.domain.member.dto.MemberSignupResponse;
import com.techeer.backend.domain.member.entity.Member;
import com.techeer.backend.domain.member.repository.MemberRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 음악 앱 회원의 가입과 인증(로그인·토큰 갱신·로그아웃). 광고주 인증(AuthService)과 같은 방식을 쓰되
 * 회원 전용 토큰(대상 MEMBER)을 발급하고 검증한다.
 */
@Service
public class MemberAuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 없는 이메일로 로그인했을 때도 비밀번호 비교를 한 번 실행하기 위한 가짜 해시.
     * 비교를 건너뛰면 "없는 이메일"이 "틀린 비밀번호"보다 눈에 띄게 빨리 응답해서 가입 여부를 알아낼 수 있다.
     */
    private final String dummyPasswordHash;

    public MemberAuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 회원을 가입시킨다. 비밀번호는 bcrypt 로 해시해 저장하고 평문은 남기지 않는다.
     *
     * <p>이메일은 소문자로 통일해 저장한다. 중복은 existsByEmail 로 먼저 거르고, 동시에 같은 이메일이 들어와 이 검사를
     * 둘 다 통과하는 경우에는 DB 의 unique 제약이 마지막으로 막으며 그 위반도 같은 409 로 바꿔 돌려준다.
     *
     * @throws BusinessException 이미 가입된 이메일인 경우
     */
    @Transactional
    public MemberSignupResponse signup(MemberSignupRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        Member member = Member.create(email, passwordEncoder.encode(request.password()), request.nickname().trim());
        try {
            // save 가 아니라 saveAndFlush: 제약 위반이 이 try 안에서 바로 드러나야 잡을 수 있다.
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        return MemberSignupResponse.from(member);
    }

    /**
     * 이메일·비밀번호를 확인하고 access/refresh token 을 발급한다.
     *
     * <p>이메일이 없을 때와 비밀번호가 틀렸을 때, 탈퇴한 계정일 때 모두 같은 INVALID_CREDENTIALS 로 응답한다.
     * 로그인할 때마다 refresh token 해시를 새 값으로 덮어쓰므로 회원당 로그인 세션은 하나만 유지된다.
     *
     * @throws BusinessException 로그인 정보가 맞지 않는 경우
     */
    @Transactional
    public TokenResponse login(MemberLoginRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);

        String hashToCompare = member == null ? dummyPasswordHash : member.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCompare);
        if (member == null || !passwordMatches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createMemberAccessToken(member.getMemberId());
        String refreshToken = jwtTokenProvider.createMemberRefreshToken(member.getMemberId());
        member.changeRefreshTokenHash(TokenHasher.sha256Hex(refreshToken));
        return TokenResponse.of(accessToken, refreshToken, jwtTokenProvider.accessTokenExpiresInSeconds());
    }

    /**
     * refresh token 으로 새 access token 을 발급한다. 이때 refresh token 도 새것으로 교체(회전)하고 이전 것은 폐기한다.
     *
     * <p>서명·만료가 맞아도 DB 에 저장된 해시와 다르면 거절한다. 같은 토큰으로 동시에 여러 번 요청해도
     * DB 의 조건부 UPDATE 덕분에 한 번만 성공한다.
     *
     * @throws BusinessException 토큰이 유효하지 않거나 이미 폐기된 경우, 광고주 토큰을 보낸 경우
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Long memberId = jwtTokenProvider.parseMemberId(request.refreshToken(), TokenType.REFRESH);

        String accessToken = jwtTokenProvider.createMemberAccessToken(memberId);
        String newRefreshToken = jwtTokenProvider.createMemberRefreshToken(memberId);

        int rotated = memberRepository.rotateRefreshTokenHash(
                memberId,
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
     * <p>이미 로그아웃했거나 이미 교체된 옛 토큰이어도 에러 없이 성공으로 처리한다. 이미 만들어 준 access token 은
     * 만료될 때까지(30분) 유효하다.
     *
     * @throws BusinessException 서명·만료·형식이 올바르지 않은 토큰인 경우
     */
    @Transactional
    public void logout(LogoutRequest request) {
        Long memberId = jwtTokenProvider.parseMemberId(request.refreshToken(), TokenType.REFRESH);
        memberRepository.clearRefreshTokenHash(memberId, TokenHasher.sha256Hex(request.refreshToken()));
    }
}
