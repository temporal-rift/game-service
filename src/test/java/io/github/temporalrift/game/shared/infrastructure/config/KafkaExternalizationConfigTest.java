package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KafkaExternalizationConfigTest {

    private final KafkaExternalizationConfig config = new KafkaExternalizationConfig();

    @Test
    @DisplayName("serializes externalization to preserve this instance's publish order")
    void gameEventsExternalizationConfiguration_serializesExternalization() {
        // when
        var result = config.gameEventsExternalizationConfiguration();

        // then
        assertThat(result.serializeExternalization()).isTrue();
    }
}
