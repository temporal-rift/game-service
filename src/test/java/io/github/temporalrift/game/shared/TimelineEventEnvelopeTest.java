package io.github.temporalrift.game.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

class TimelineEventEnvelopeTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID AGGREGATE_ID = UUID.randomUUID();
    private static final UUID GAME_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    @DisplayName("reads the envelope from the headers timeline-service publishes")
    void from_publishedHeaders_readsEveryField() {
        var message = MessageBuilder.withPayload((Object) "payload")
                .copyHeaders(Map.of(
                        "eventType",
                        "ParadoxCascaded",
                        "eventId",
                        EVENT_ID.toString(),
                        "aggregateId",
                        AGGREGATE_ID.toString(),
                        "aggregateType",
                        "FutureEvent",
                        "gameId",
                        GAME_ID.toString(),
                        "occurredAt",
                        OCCURRED_AT,
                        "version",
                        1))
                .build();

        var envelope = TimelineEventEnvelope.from(message);

        assertThat(envelope)
                .isEqualTo(new TimelineEventEnvelope(
                        EVENT_ID, "ParadoxCascaded", AGGREGATE_ID, "FutureEvent", GAME_ID, OCCURRED_AT, 1));
    }

    @Test
    @DisplayName("reads occurredAt and version delivered as raw bytes by an untrusting header mapper")
    void from_rawByteHeaders_parsesTypedFields() {
        var message = MessageBuilder.withPayload((Object) "payload")
                .copyHeaders(Map.of(
                        "eventType", utf8("OutcomeApplied"),
                        "eventId", utf8(EVENT_ID.toString()),
                        "gameId", utf8(GAME_ID.toString()),
                        "occurredAt", utf8("\"" + OCCURRED_AT + "\""),
                        "version", utf8("1")))
                .build();

        var envelope = TimelineEventEnvelope.from(message);

        assertThat(envelope.eventType()).isEqualTo("OutcomeApplied");
        assertThat(envelope.eventId()).isEqualTo(EVENT_ID);
        assertThat(envelope.gameId()).isEqualTo(GAME_ID);
        assertThat(envelope.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(envelope.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("absent headers read as null rather than failing")
    void from_missingHeaders_readsNulls() {
        var message = MessageBuilder.withPayload((Object) "payload")
                .setHeader("eventType", "OutcomeApplied")
                .build();

        var envelope = TimelineEventEnvelope.from(message);

        assertThat(envelope.eventId()).isNull();
        assertThat(envelope.aggregateId()).isNull();
        assertThat(envelope.aggregateType()).isNull();
        assertThat(envelope.gameId()).isNull();
        assertThat(envelope.occurredAt()).isNull();
        assertThat(envelope.version()).isNull();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
