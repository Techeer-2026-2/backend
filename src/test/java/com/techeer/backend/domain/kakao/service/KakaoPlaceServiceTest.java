package com.techeer.backend.domain.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.techeer.backend.domain.kakao.dto.PlaceSearchResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoPlaceServiceTest {

    @Test
    void 검색_결과를_PlaceSearchResponse로_변환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://dapi.kakao.com/v2/local/search/keyword.json?query={query}", "강남역"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            { "place_name": "강남역", "address_name": "서울 강남구", "x": "127.02", "y": "37.49" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        KakaoPlaceService service = new KakaoPlaceService(builder.build());

        List<PlaceSearchResponse> result = service.search("강남역");

        assertThat(result).containsExactly(new PlaceSearchResponse("강남역", "서울 강남구", 37.49, 127.02));
    }

    @Test
    void 카카오_호출이_실패하면_EXTERNAL_API_ERROR_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://dapi.kakao.com/v2/local/search/keyword.json?query={query}", "강남역"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        KakaoPlaceService service = new KakaoPlaceService(builder.build());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.search("강남역"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void 응답_본문이_비어_있으면_EXTERNAL_API_ERROR_예외를_던진다() {
        assertExternalError("{}");
    }

    @Test
    void 좌표가_숫자가_아니면_EXTERNAL_API_ERROR_예외를_던진다() {
        assertExternalError("""
                { "documents": [ { "place_name": "강남역", "address_name": "서울", "x": "abc", "y": "37.49" } ] }
                """);
    }

    @Test
    void 좌표가_없으면_EXTERNAL_API_ERROR_예외를_던진다() {
        assertExternalError("""
                { "documents": [ { "place_name": "강남역", "address_name": "서울" } ] }
                """);
    }

    private void assertExternalError(String responseBody) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://dapi.kakao.com/v2/local/search/keyword.json?query={query}", "강남역"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        KakaoPlaceService service = new KakaoPlaceService(builder.build());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.search("강남역"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }
}
