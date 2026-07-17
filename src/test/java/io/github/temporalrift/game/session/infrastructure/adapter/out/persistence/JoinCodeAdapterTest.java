package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JoinCodeAdapterTest {

    private final JoinCodeAdapter adapter = new JoinCodeAdapter();

    @Test
    @DisplayName("returns an 8-character code from the unambiguous alphabet")
    void generate_returnsValidCode() {
        var code = adapter.generate();

        assertThat(code).hasSize(8).matches("[23456789ABCDEFGHJKMNPQRSTVWXYZ]+");
    }

    @Test
    @DisplayName("codes are unique across generations")
    void generate_producesDistinctCodes() {
        var codes = IntStream.range(0, 1000)
                .mapToObj(i -> adapter.generate())
                .distinct()
                .count();

        assertThat(codes).isEqualTo(1000);
    }
}
