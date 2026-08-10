package io.github.temporalrift.game.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest(name = "version header {0}")
    @MethodSource("inexactVersions")
    @DisplayName("a version that is not exactly an int reads as unsupported, never as a truncated one")
    void from_inexactVersion_readsAsUnsupported(Object headerValue) {
        var message = MessageBuilder.withPayload((Object) "payload")
                .setHeader("eventId", EVENT_ID.toString())
                .setHeader("version", headerValue)
                .build();

        var envelope = TimelineEventEnvelope.from(message);

        assertThat(envelope.version()).isNull();
        assertThat(envelope.hasVersion(1)).isFalse();
    }

    private static Stream<Object> inexactVersions() {
        // Each would truncate to the supported version 1 and let the consumer claim an event it cannot handle.
        return Stream.of(1.5d, 4294967297L, utf8("1.5"), utf8("not-a-number"));
    }

    @Test
    @DisplayName("the header gameId matches only the payload game it was routed for")
    void matchesGameId_comparesAgainstThePartitionKey() {
        var envelope = new TimelineEventEnvelope(
                EVENT_ID, "ParadoxCascaded", AGGREGATE_ID, "FutureEvent", GAME_ID, OCCURRED_AT, 1);

        assertThat(envelope.matchesGameId(GAME_ID)).isTrue();
        assertThat(envelope.matchesGameId(UUID.randomUUID())).isFalse();
        assertThat(envelope.matchesGameId(null)).isFalse();
    }

    @Test
    @DisplayName("an absent header gameId never matches a payload game")
    void matchesGameId_withoutHeader_neverMatches() {
        var envelope = new TimelineEventEnvelope(
                EVENT_ID, "ParadoxCascaded", AGGREGATE_ID, "FutureEvent", null, OCCURRED_AT, 1);

        assertThat(envelope.matchesGameId(GAME_ID)).isFalse();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
