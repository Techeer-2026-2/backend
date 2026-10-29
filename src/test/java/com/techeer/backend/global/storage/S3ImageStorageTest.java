package com.techeer.backend.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 를 실제로 호출하지 않고, 어떤 키로 올리고 지우는지와 URL 을 어떻게 만드는지만 확인한다.
 */
class S3ImageStorageTest {

    private static final String BASE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com";

    private final S3Client s3Client = mock(S3Client.class);
    private final S3ImageStorage storage = new S3ImageStorage(s3Client, "bucket", BASE_URL + "/");

    private final MockMultipartFile image = new MockMultipartFile("image", "배너.png", "image/png", new byte[] {1});

    @Test
    @DisplayName("폴더/무작위이름.확장자 키로 올리고 공개 URL 을 돌려준다")
    void uploadsWithRandomKey() {
        String url = storage.upload("campaign-banners", image, ImageType.PNG);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("bucket");
        assertThat(request.getValue().key()).matches("campaign-banners/[0-9a-f-]{36}\\.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo(BASE_URL + "/" + request.getValue().key());
    }

    @Test
    @DisplayName("S3 호출이 실패하거나 버킷이 설정되지 않았으면 IMAGE_UPLOAD_FAILED 를 던진다")
    void failsWhenUploadFails() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("연결 실패"));

        assertThatThrownBy(() -> storage.upload("campaign-banners", image, ImageType.PNG))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);

        S3ImageStorage noBucket = new S3ImageStorage(s3Client, "", BASE_URL);
        assertThatThrownBy(() -> noBucket.upload("campaign-banners", image, ImageType.PNG))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);
    }

    @Test
    @DisplayName("내 버킷 URL 이면 그 키를 지우고, 외부 URL 이면 아무것도 하지 않는다")
    void deletesOnlyManagedImages() {
        storage.deleteIfManaged(BASE_URL + "/campaign-banners/a.png");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("campaign-banners/a.png");

        storage.deleteIfManaged("https://example.com/banner.png");
        storage.deleteIfManaged(null);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("삭제가 실패해도 예외를 던지지 않는다")
    void ignoresDeleteFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("연결 실패"));

        storage.deleteIfManaged(BASE_URL + "/campaign-banners/a.png");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
