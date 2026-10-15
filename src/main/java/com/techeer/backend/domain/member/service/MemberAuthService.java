package com.techeer.backend.domain.member.service;

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
 * 음악 앱 회원의 가입. 광고주 가입(AdvertiserService)과 같은 방식을 쓴다.
 */
@Service
public class MemberAuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberAuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
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
}
