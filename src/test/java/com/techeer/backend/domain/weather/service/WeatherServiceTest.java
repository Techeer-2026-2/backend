package com.techeer.backend.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeatherServiceTest {

    @Test
    void Clear는_CLEAR로_변환한다() {
        assertThat(callWithMain("Clear")).isEqualTo(WeatherCondition.CLEAR);
    }

    @Test
    void Clouds는_CLOUDY로_변환한다() {
        assertThat(callWithMain("Clouds")).isEqualTo(WeatherCondition.CLOUDY);
    }

    @Test
    void Rain은_RAINY로_변환한다() {
        assertThat(callWithMain("Rain")).isEqualTo(WeatherCondition.RAINY);
    }

    @Test
    void Thunderstorm도_RAINY로_변환한다() {
        assertThat(callWithMain("Thunderstorm")).isEqualTo(WeatherCondition.RAINY);
    }

    @Test
    void Snow는_SNOWY로_변환한다() {
        assertThat(callWithMain("Snow")).isEqualTo(WeatherCondition.SNOWY);
    }

    @Test
    void 알_수_없는_날씨는_CLOUDY로_뭉뚱그린다() {
        assertThat(callWithMain("Mist")).isEqualTo(WeatherCondition.CLOUDY);
    }

    @Test
    void 호출이_실패하면_EXTERNAL_API_ERROR_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openweathermap.org/data/2.5");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid=test-key",
                        37.5, 127.0))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        WeatherService service = new WeatherService(builder.build(), "test-key");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.getCurrentCondition(37.5, 127.0));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void 응답에_날씨_정보가_없으면_EXTERNAL_API_ERROR_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openweathermap.org/data/2.5");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid=test-key",
                        37.5, 127.0))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        WeatherService service = new WeatherService(builder.build(), "test-key");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.getCurrentCondition(37.5, 127.0));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    private WeatherCondition callWithMain(String main) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openweathermap.org/data/2.5");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid=test-key",
                        37.5, 127.0))
                .andRespond(withSuccess("""
                        { "weather": [ { "main": "%s" } ] }
                        """.formatted(main), MediaType.APPLICATION_JSON));

        WeatherService service = new WeatherService(builder.build(), "test-key");
        return service.getCurrentCondition(37.5, 127.0);
    }
}
