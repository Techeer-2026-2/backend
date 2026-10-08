package com.techeer.backend.domain.tmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.techeer.backend.domain.tmap.TransportMode;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TmapDurationServiceTest {

    @Test
    void 자동차_소요시간을_분으로_변환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.openapi.sk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://apis.openapi.sk.com/tmap/routes?version=1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "features": [
                            { "properties": { "totalTime": 1800 } }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmapDurationService service = new TmapDurationService(builder.build());

        int minutes = service.getDurationMinutes(127.0, 37.5, 127.1, 37.6, TransportMode.CAR);

        assertThat(minutes).isEqualTo(30);
    }

    @Test
    void 대중교통_소요시간을_분으로_변환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.openapi.sk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://apis.openapi.sk.com/transit/routes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "metaData": {
                            "plan": {
                              "itineraries": [
                                { "totalTime": 900 }
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        TmapDurationService service = new TmapDurationService(builder.build());

        int minutes = service.getDurationMinutes(127.0, 37.5, 127.1, 37.6, TransportMode.PUBLIC_TRANSIT);

        assertThat(minutes).isEqualTo(15);
    }

    @Test
    void TMAP_호출이_실패하면_EXTERNAL_API_ERROR_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.openapi.sk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://apis.openapi.sk.com/tmap/routes?version=1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        TmapDurationService service = new TmapDurationService(builder.build());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.getDurationMinutes(127.0, 37.5, 127.1, 37.6, TransportMode.CAR));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void 자동차_경로가_비어_있으면_EXTERNAL_API_ERROR_예외를_던진다() {
        assertExternalError("/tmap/routes?version=1", TransportMode.CAR, """
                { "features": [] }
                """);
    }

    @Test
    void 자동차_응답에_소요시간이_없으면_EXTERNAL_API_ERROR_예외를_던진다() {
        assertExternalError("/tmap/routes?version=1", TransportMode.CAR, """
                { "features": [ { "properties": {} } ] }
                """);
    }

    @Test
    void 대중교통_경로가_비어_있으면_EXTERNAL_API_ERROR_예외를_던진다() {
        assertExternalError("/transit/routes", TransportMode.PUBLIC_TRANSIT, """
                { "metaData": { "plan": { "itineraries": [] } } }
                """);
    }

    @Test
    void 대중교통_응답에_plan이_없으면_EXTERNAL_API_ERROR_예외를_던진다() {
        assertExternalError("/transit/routes", TransportMode.PUBLIC_TRANSIT, """
                { "metaData": {} }
                """);
    }

    private void assertExternalError(String path, TransportMode mode, String responseBody) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.openapi.sk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://apis.openapi.sk.com" + path))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        TmapDurationService service = new TmapDurationService(builder.build());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.getDurationMinutes(127.0, 37.5, 127.1, 37.6, mode));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }
}
