package com.techeer.backend.domain.commuteprofile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileCreateRequest;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileResponse;
import com.techeer.backend.domain.commuteprofile.dto.LocationDto;
import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.tmap.TransportMode;
import com.techeer.backend.domain.tmap.service.TmapDurationService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommuteProfileServiceTest {

    private final CommuteProfileRepository commuteProfileRepository = mock(CommuteProfileRepository.class);
    private final TmapDurationService tmapDurationService = mock(TmapDurationService.class);
    private final CommuteProfileService commuteProfileService =
            new CommuteProfileService(commuteProfileRepository, tmapDurationService);

    @Test
    void 등록하면_TMAP으로_평균_소요시간을_자동_계산한다() {
        when(tmapDurationService.getDurationMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(25);
        when(commuteProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CommuteProfileCreateRequest request = new CommuteProfileCreateRequest(
                CommuteType.TO_WORK,
                TransportMode.PUBLIC_TRANSIT,
                "2호선",
                new LocationDto(37.5, 127.0, "집"),
                new LocationDto(37.6, 127.1, "회사"));

        CommuteProfileResponse response = commuteProfileService.create(1L, request);

        assertThat(response.averageDurationMinutes()).isEqualTo(25);
    }

    @Test
    void 존재하지_않는_프로필_조회시_COMMUTE_PROFILE_NOT_FOUND_예외() {
        when(commuteProfileRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> commuteProfileService.findDetail(1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMUTE_PROFILE_NOT_FOUND);
    }

    @Test
    void 다른_회원의_프로필_조회시_COMMUTE_PROFILE_NOT_FOUND_예외() {
        CommuteProfile profile = sampleProfile(2L);
        when(commuteProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> commuteProfileService.findDetail(1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMUTE_PROFILE_NOT_FOUND);
    }

    @Test
    void 삭제하면_repository_delete가_호출된다() {
        CommuteProfile profile = sampleProfile(1L);
        when(commuteProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        commuteProfileService.delete(1L, 1L);

        verify(commuteProfileRepository).delete(profile);
    }

    private CommuteProfile sampleProfile(Long memberId) {
        return CommuteProfile.builder()
                .memberId(memberId)
                .commuteType(CommuteType.TO_WORK)
                .transportMode(TransportMode.CAR)
                .frequentRouteName("경부고속도로")
                .averageDurationMinutes(30)
                .departure(new Location(37.5, 127.0, "집"))
                .arrival(new Location(37.6, 127.1, "회사"))
                .build();
    }
}
