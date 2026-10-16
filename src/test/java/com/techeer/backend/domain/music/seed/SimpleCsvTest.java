package com.techeer.backend.domain.music.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleCsvTest {

    @Test
    void 따옴표_안의_쉼표와_줄바꿈과_이중_따옴표를_읽는다() {
        List<List<String>> rows = SimpleCsv.parse("a,\"b,c\",\"d\"\"e\"\n1,\"x\ny\",\n");

        assertThat(rows).containsExactly(
                List.of("a", "b,c", "d\"e"),
                List.of("1", "x\ny", ""));
    }

    @Test
    void 윈도우_줄바꿈과_마지막_줄바꿈_없음도_읽는다() {
        assertThat(SimpleCsv.parse("a,b\r\nc,d")).containsExactly(List.of("a", "b"), List.of("c", "d"));
    }
}
