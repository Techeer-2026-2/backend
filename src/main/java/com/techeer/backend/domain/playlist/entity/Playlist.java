package com.techeer.backend.domain.playlist.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "playlists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String name;

    private String description;

    private Boolean isPublic;

    /** 기존 행(이 칼럼이 생기기 전)은 직접 만든 것으로 본다. 운영 DB 에는 DEFAULT 'USER_CREATED' 로 칼럼을 추가한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'USER_CREATED'")
    private PlaylistType type = PlaylistType.USER_CREATED;

    /** 공유 링크 토큰. 공유 중일 때만 값이 있고(UUID), 공유를 취소하면 null 로 돌아간다. */
    @Column(unique = true, length = 36)
    private String shareToken;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlaylistTrack> tracks = new ArrayList<>();

    @Builder
    private Playlist(Long memberId, String name, Boolean isPublic, PlaylistType type) {
        this.memberId = memberId;
        this.name = name;
        this.isPublic = isPublic != null ? isPublic : true;
        this.type = type == null ? PlaylistType.USER_CREATED : type;
    }

    public void update(String name, String description, Boolean isPublic) {
        this.name = name;
        this.description = description;
        this.isPublic = isPublic;
    }

    /** 공유 토큰을 만든다. 이미 공유 중이면 기존 토큰을 그대로 돌려준다(멱등). */
    public String share() {
        if (shareToken == null) {
            shareToken = UUID.randomUUID().toString();
        }
        return shareToken;
    }

    /** 공유를 취소한다. 이전 토큰으로는 더 이상 조회할 수 없다. */
    public void revokeShare() {
        this.shareToken = null;
    }

    /** 담긴 곡들의 재생시간 합계(초). 재생시간을 모르는 곡은 0초로 친다. */
    public int calculateTotalDurationSec() {
        long totalMillis = tracks.stream()
                .map(playlistTrack -> playlistTrack.getTrack().getTrackTimeMillis())
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        return (int) (totalMillis / 1000);
    }
}
