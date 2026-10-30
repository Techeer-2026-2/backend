package com.techeer.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 모든 도메인이 함께 쓰는 에러 코드 목록. 팀원 모두가 이 파일에 상수를 추가하므로 충돌이 잦다.
 * 충돌과 이름 중복을 줄이려고 아래 규칙을 지킨다 (자세한 설명은 CONTRIBUTING.md 의 ErrorCode 규칙).
 *
 * <ul>
 *   <li>새 상수 이름은 도메인 접두사로 시작한다 (예: {@code PLAYLIST_NOT_FOUND}). 기존 이름은 바꾸지 않는다.
 *   <li>새 상수는 자기 도메인 구역의 <b>맨 위(구역 주석 바로 아래)</b>에 추가한다. 파일 끝에 추가하면
 *       여러 사람이 같은 줄을 고쳐 충돌한다. 도메인 구역이 없으면 파일 끝이 아니라 기존 구역 사이에 새로 만든다.
 *   <li>모든 상수 끝에 쉼표(,)를 붙인다. 마지막 {@code ;} 는 따로 한 줄로 둬서 마지막 상수를 고치지 않아도 된다.
 *   <li>PR 을 올리기 전에 develop 을 합치고 다시 컴파일한다 (같은 이름은 git 이 알려 주지 않고 컴파일에서만 깨진다).
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    // 캠페인
    INVALID_CAMPAIGN_PERIOD(HttpStatus.BAD_REQUEST, "노출 종료 시각은 시작 시각보다 뒤여야 합니다."),
    CAMPAIGN_PERIOD_ALREADY_ENDED(HttpStatus.BAD_REQUEST, "노출 종료 시각은 현재 시각보다 뒤여야 합니다."),
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 캠페인입니다."),
    CAMPAIGN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "내 캠페인이 아닙니다."),
    CAMPAIGN_FIELD_NOT_EDITABLE(HttpStatus.CONFLICT, "진행중인 캠페인은 문구와 배너 이미지만 수정할 수 있습니다."),
    CAMPAIGN_ENDED(HttpStatus.CONFLICT, "종료된 캠페인은 수정할 수 없습니다."),

    // 이미지 업로드
    IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "빈 이미지 파일은 올릴 수 없습니다."),
    IMAGE_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "png, jpg, webp 이미지만 올릴 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지는 5MB, 요청 전체는 6MB 까지 올릴 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "이미지 업로드에 실패했습니다."),

    // 광고주
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ADVERTISER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 광고주입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),

    // 타겟 유저
    TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),

    // 노출
    IMPRESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림(notification_id)입니다."),

    // 음악 앱 회원 식별
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않거나 탈퇴한 회원입니다."),
    MISSING_MEMBER_ID(HttpStatus.BAD_REQUEST, "X-Member-Id 헤더가 필요합니다."),
    INVALID_MEMBER_ID(HttpStatus.BAD_REQUEST, "X-Member-Id 헤더 값이 올바르지 않습니다."),

    // 음악 랭킹
    TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 곡을 찾을 수 없습니다."),
    PLAYBACK_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "재생 시작 세션을 찾을 수 없습니다."),
    PLAYBACK_CONFLICT(HttpStatus.CONFLICT, "이미 사용된 이벤트 또는 세션 ID의 내용이 다릅니다."),

    // 음악 앱 플레이리스트
    PLAYLIST_EMPTY(HttpStatus.CONFLICT, "곡을 추가한 뒤 플레이리스트를 공유할 수 있습니다."),
    PLAYLIST_GENERATE_NO_CANDIDATES(HttpStatus.NOT_FOUND, "추천할 곡을 찾지 못했습니다."),
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 플레이리스트입니다."),
    PLAYLIST_TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 트랙입니다."),

    // 외부 API (카카오·TMAP·iTunes 등)
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다."),

    // 음악 앱 통근 세션
    COMMUTE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않거나 진행 중인 통근이 없습니다."),
    COMMUTE_SESSION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 진행 중인 통근이 있습니다."),
    COMMUTE_SESSION_NOT_ACTIVE(HttpStatus.CONFLICT, "진행 중인 통근이 아닙니다."),
    COMMUTE_SESSION_EVENT_CONFLICT(HttpStatus.CONFLICT, "이미 사용된 이벤트 ID 의 내용이 다릅니다."),

    // 음악 앱 통근 프로필
    COMMUTE_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 통근 프로필입니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
