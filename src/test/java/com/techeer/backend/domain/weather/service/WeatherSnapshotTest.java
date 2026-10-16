package com.techeer.backend.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeatherSnapshotTest {

    private WeatherSnapshot call(String json) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openweathermap.org/data/2.5");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestToUriTemplate(
                        "https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid=test-key",
                        37.5, 127.0))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        return new WeatherService(builder.build(), "test-key").getCurrentSnapshot(37.5, 127.0);
    }

    @Test
    void 켈빈_기온을_섭씨로_바꾼다() {
        WeatherSnapshot snapshot = call("{\"weather\":[{\"main\":\"Clear\"}],\"main\":{\"temp\":295.15}}");

        assertThat(snapshot.condition()).isEqualTo(WeatherCondition.CLEAR);
        assertThat(snapshot.temperatureCelsius()).isCloseTo(22.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(snapshot.hazy()).isFalse();
        assertThat(snapshot.stormy()).isFalse();
    }

    @Test
    void 안개와_연무는_hazy_이고_상태는_CLOUDY_로_본다() {
        WeatherSnapshot snapshot = call("{\"weather\":[{\"main\":\"Haze\"}],\"main\":{\"temp\":280.0}}");

        assertThat(snapshot.hazy()).isTrue();
        assertThat(snapshot.condition()).isEqualTo(WeatherCondition.CLOUDY);
    }

    @Test
    void 천둥번개는_stormy_이고_상태는_RAINY_로_본다() {
        WeatherSnapshot snapshot = call("{\"weather\":[{\"main\":\"Thunderstorm\"}],\"main\":{\"temp\":290.0}}");

        assertThat(snapshot.stormy()).isTrue();
        assertThat(snapshot.condition()).isEqualTo(WeatherCondition.RAINY);
    }

    @Test
    void 기온이_응답에_없으면_null_이다() {
        assertThat(call("{\"weather\":[{\"main\":\"Snow\"}]}").temperatureCelsius()).isNull();
    }
}
