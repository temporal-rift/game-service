package io.github.temporalrift.game.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DomainEventEnvelopeTest {

    @Test
    void create_usesProvidedClockForOccurredAt() {
        var occurredAt = Instant.parse("2042-03-04T05:06:07Z");
        var clock = Clock.fixed(occurredAt, ZoneOffset.UTC);

        var envelope = DomainEventEnvelope.create(
                UUID.randomUUID(), "game", UUID.randomUUID(), DomainEventEnvelope.SCHEMA_VERSION_V1, "payload", clock);

        assertThat(envelope.occurredAt()).isEqualTo(occurredAt);
        assertThat(envelope.version()).isEqualTo(DomainEventEnvelope.SCHEMA_VERSION_V1);
    }
}
