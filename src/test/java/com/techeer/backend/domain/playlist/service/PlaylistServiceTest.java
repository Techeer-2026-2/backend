package com.techeer.backend.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.playlist.dto.PlaylistCreateRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistTrackAddRequest;
import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaylistServiceTest {

    private final PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
    private final PlaylistService playlistService = new PlaylistService(playlistRepository);

    @Test
    void 플레이리스트를_생성한다() {
        when(playlistRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlaylistResponse response = playlistService.create(1L, new PlaylistCreateRequest("출근길"));

        assertThat(response.name()).isEqualTo("출근길");
    }

    @Test
    void 존재하지_않는_플레이리스트_조회시_PLAYLIST_NOT_FOUND_예외() {
        when(playlistRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> playlistService.findDetail(1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_NOT_FOUND);
    }

    @Test
    void 다른_회원의_플레이리스트_조회시_PLAYLIST_NOT_FOUND_예외() {
        Playlist playlist = Playlist.builder().memberId(2L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> playlistService.findDetail(1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_NOT_FOUND);
    }

    @Test
    void 내_플레이리스트는_정상_조회된다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        PlaylistDetailResponse detail = playlistService.findDetail(1L, 1L);

        assertThat(detail.name()).isEqualTo("출근길");
    }

    @Test
    void 곡을_추가하면_트랙_개수가_늘어난다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        playlistService.addTracks(1L, 1L, List.of(
                new PlaylistTrackAddRequest(100L, "밤편지", "아이유", "밤편지", null, null, 240000)));

        assertThat(playlist.getTracks()).hasSize(1);
    }

    @Test
    void 존재하지_않는_트랙_삭제시_PLAYLIST_TRACK_NOT_FOUND_예외() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> playlistService.removeTrack(1L, 1L, 999L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_TRACK_NOT_FOUND);
    }

    @Test
    void 플레이리스트를_삭제하면_repository_delete가_호출된다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        playlistService.delete(1L, 1L);

        verify(playlistRepository).delete(playlist);
    }
}
