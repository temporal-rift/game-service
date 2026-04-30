package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventExternalizationConfiguration;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.LobbyCreated;

class KafkaExternalizationConfigTest {

    final KafkaExternalizationConfig config = new KafkaExternalizationConfig();

    @Test
    @DisplayName("bean is created without error and is not null")
    void bean_createsSuccessfully() {
        // when
        var result = config.eventExternalizationConfiguration();

        // then
        assertThat(result).isNotNull().isInstanceOf(EventExternalizationConfiguration.class);
    }

    @Test
    @DisplayName("EventEnvelope instances are selected for externalization")
    void select_eventEnvelope_isSelected() {
        // given
        var cfg = config.eventExternalizationConfiguration();
        var envelope = new EventEnvelope(
                UUID.randomUUID(),
                "session.LobbyCreated",
                UUID.randomUUID(),
                "Lobby",
                UUID.randomUUID(),
                Instant.now(),
                1,
                new LobbyCreated(UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        // when
        var selected = cfg.supports(envelope);

        // then
        assertThat(selected).isTrue();
    }
}
