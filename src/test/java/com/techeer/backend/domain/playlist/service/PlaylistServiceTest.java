package com.techeer.backend.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.playlist.dto.PlaylistCreateRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistTrackAddRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistUpdateRequest;
import com.techeer.backend.domain.playlist.dto.TrackOrderUpdateRequest;
import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.PlaylistTrack;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class PlaylistServiceTest {

    private final PlaylistRepository playlistRepository = mock(PlaylistRepository.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PlaylistService playlistService =
            new PlaylistService(playlistRepository, trackRepository, transactionManager);

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
    void 처음_추가하는_곡은_track이_새로_생성된다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findByItunesTrackId(100L)).thenReturn(Optional.empty());
        when(trackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        playlistService.addTracks(1L, 1L, List.of(
                new PlaylistTrackAddRequest(100L, "밤편지", "아이유", "밤편지", null, null, 240000, "Ballad")));

        assertThat(playlist.getTracks()).hasSize(1);
        assertThat(playlist.getTracks().get(0).getTrack().getTitle()).isEqualTo("밤편지");
        verify(trackRepository).save(any());
    }

    @Test
    void 이미_있는_곡을_추가하면_기존_track을_재사용한다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        Track existingTrack = Track.builder().itunesTrackId(100L).title("밤편지").artist("아이유").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findByItunesTrackId(100L)).thenReturn(Optional.of(existingTrack));

        playlistService.addTracks(1L, 1L, List.of(
                new PlaylistTrackAddRequest(100L, "밤편지", "아이유", "밤편지", null, null, 240000, "Ballad")));

        assertThat(playlist.getTracks().get(0).getTrack()).isEqualTo(existingTrack);
        verify(trackRepository, never()).save(any());
    }

    @Test
    void 동시에_같은_곡이_추가돼서_저장이_충돌나면_재조회해서_재사용한다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        Track existingTrack = Track.builder().itunesTrackId(100L).title("밤편지").artist("아이유").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findByItunesTrackId(100L))
                .thenReturn(Optional.empty(), Optional.of(existingTrack));
        when(trackRepository.save(any())).thenThrow(new DataIntegrityViolationException("중복 키"));

        playlistService.addTracks(1L, 1L, List.of(
                new PlaylistTrackAddRequest(100L, "밤편지", "아이유", "밤편지", null, null, 240000, "Ballad")));

        assertThat(playlist.getTracks().get(0).getTrack()).isEqualTo(existingTrack);
    }

    @Test
    void 이름만_수정하면_나머지_필드는_유지된다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        PlaylistResponse response = playlistService.update(1L, 1L,
                new PlaylistUpdateRequest("새이름", null, null));

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(response.isPublic()).isTrue();
    }

    @Test
    void 공개범위만_수정하면_이름은_유지된다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        PlaylistResponse response = playlistService.update(1L, 1L,
                new PlaylistUpdateRequest(null, null, false));

        assertThat(response.name()).isEqualTo("출근길");
        assertThat(response.isPublic()).isFalse();
    }

    @Test
    void 곡_순서를_요청한_순서대로_반영한다() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        Track track1 = Track.builder().itunesTrackId(1L).title("A").build();
        Track track2 = Track.builder().itunesTrackId(2L).title("B").build();
        PlaylistTrack playlistTrack1 = PlaylistTrack.builder().playlist(playlist).track(track1).build();
        PlaylistTrack playlistTrack2 = PlaylistTrack.builder().playlist(playlist).track(track2).build();
        setId(playlistTrack1, 10L);
        setId(playlistTrack2, 20L);
        playlist.getTracks().add(playlistTrack1);
        playlist.getTracks().add(playlistTrack2);
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        playlistService.updateTrackOrder(1L, 1L, new TrackOrderUpdateRequest(List.of(20L, 10L)));

        assertThat(playlistTrack2.getTrackOrder()).isEqualTo(0);
        assertThat(playlistTrack1.getTrackOrder()).isEqualTo(1);
    }

    @Test
    void 곡_순서_변경시_개수가_안_맞으면_PLAYLIST_TRACK_NOT_FOUND_예외() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        Track track1 = Track.builder().itunesTrackId(1L).title("A").build();
        PlaylistTrack playlistTrack1 = PlaylistTrack.builder().playlist(playlist).track(track1).build();
        setId(playlistTrack1, 10L);
        playlist.getTracks().add(playlistTrack1);
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> playlistService.updateTrackOrder(1L, 1L, new TrackOrderUpdateRequest(List.of(10L, 999L))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_TRACK_NOT_FOUND);
    }

    @Test
    void 곡_순서_변경시_중복된_ID가_있으면_PLAYLIST_TRACK_NOT_FOUND_예외() {
        Playlist playlist = Playlist.builder().memberId(1L).name("출근길").build();
        Track track1 = Track.builder().itunesTrackId(1L).title("A").build();
        Track track2 = Track.builder().itunesTrackId(2L).title("B").build();
        PlaylistTrack playlistTrack1 = PlaylistTrack.builder().playlist(playlist).track(track1).build();
        PlaylistTrack playlistTrack2 = PlaylistTrack.builder().playlist(playlist).track(track2).build();
        setId(playlistTrack1, 10L);
        setId(playlistTrack2, 20L);
        playlist.getTracks().add(playlistTrack1);
        playlist.getTracks().add(playlistTrack2);
        when(playlistRepository.findById(1L)).thenReturn(Optional.of(playlist));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> playlistService.updateTrackOrder(1L, 1L, new TrackOrderUpdateRequest(List.of(10L, 10L))));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_TRACK_NOT_FOUND);
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

    private void setId(PlaylistTrack playlistTrack, Long id) {
        ReflectionTestUtils.setField(playlistTrack, "id", id);
    }
}
