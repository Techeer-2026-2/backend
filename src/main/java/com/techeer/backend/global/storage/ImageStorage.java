package com.techeer.backend.global.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드한 이미지를 보관하는 곳. 지금 구현체는 S3 하나이고, 테스트에서는 가짜로 바꿔 끼운다.
 */
public interface ImageStorage {

    /**
     * 이미지를 저장하고 누구나 읽을 수 있는 URL 을 돌려준다.
     *
     * @param directory 저장할 폴더 (예: campaign-banners)
     * @param type {@link ImageType#of} 로 확인한 형식
     * @throws com.techeer.backend.global.exception.BusinessException 저장에 실패한 경우
     */
    String upload(String directory, MultipartFile image, ImageType type);

    /**
     * 이 저장소에 올린 이미지면 지운다. 업로드 기능 전에 등록된 외부 URL 이면 아무것도 하지 않는다.
     * 지우기에 실패해도 예외를 던지지 않는다 (요청 자체는 이미 끝난 뒤에 호출되기 때문).
     */
    void deleteIfManaged(String imageUrl);
}
