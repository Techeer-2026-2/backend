package com.techeer.backend.global.storage;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드를 허용하는 이미지 형식. 저장할 파일의 확장자와 Content-Type 을 여기서 정한다.
 * 크기 제한(5MB)은 spring.servlet.multipart 설정이 컨트롤러 앞에서 막는다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageType {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    /**
     * 업로드된 파일의 형식을 확인한다. 브라우저가 보낸 파일의 Content-Type 을 기준으로 한다.
     *
     * @throws BusinessException 빈 파일이거나 허용하지 않는 형식인 경우
     */
    public static ImageType of(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_EMPTY);
        }
        return Arrays.stream(values())
                .filter(type -> type.contentType.equalsIgnoreCase(file.getContentType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_UNSUPPORTED_TYPE));
    }
}
