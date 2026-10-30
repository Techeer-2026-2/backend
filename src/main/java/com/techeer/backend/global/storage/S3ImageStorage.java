package com.techeer.backend.global.storage;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 이미지를 S3 에 저장한다. 버킷은 읽기 공개로 두고, 응답에는 {@code public-base-url/키} 형태의 URL 을 준다.
 */
@Slf4j
@Component
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ImageStorage(
            S3Client s3Client,
            @Value("${storage.s3.bucket}") String bucket,
            @Value("${storage.s3.region}") String region,
            @Value("${storage.s3.public-base-url}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        // 환경변수가 빈 문자열로 넘어오면 yml 기본값이 적용되지 않으므로 여기서 기본 주소를 만든다.
        String baseUrl = publicBaseUrl.isBlank() ? defaultBaseUrl(bucket, region) : publicBaseUrl.strip();
        this.publicBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 버킷 이름에 점이 있으면 가상 호스트 주소(버킷.s3...)가 S3 인증서와 맞지 않으므로 경로 방식(s3.../버킷)을 쓴다.
     */
    private static String defaultBaseUrl(String bucket, String region) {
        String host = "s3." + region + ".amazonaws.com";
        return bucket.contains(".") ? "https://" + host + "/" + bucket : "https://" + bucket + "." + host;
    }

    @Override
    public String upload(String directory, MultipartFile image, ImageType type) {
        if (bucket.isBlank()) {
            log.warn("S3_BUCKET 이 설정되지 않아 이미지를 올릴 수 없습니다.");
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        // 원래 파일 이름은 쓰지 않는다. 겹치지 않고, 경로 조작이나 한글 인코딩 문제가 생기지 않게 한다.
        String key = directory + "/" + UUID.randomUUID() + "." + type.getExtension();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(type.getContentType())
                .build();
        try (InputStream content = image.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(content, image.getSize()));
        } catch (IOException | SdkException e) {
            log.warn("S3 이미지 업로드 실패: key={}", key, e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, e);
        }
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void deleteIfManaged(String imageUrl) {
        String prefix = publicBaseUrl + "/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            return;
        }
        String key = imageUrl.substring(prefix.length());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("S3 이미지 삭제 실패, 버킷에 남은 파일은 직접 지워야 합니다: key={}", key, e);
        }
    }
}
