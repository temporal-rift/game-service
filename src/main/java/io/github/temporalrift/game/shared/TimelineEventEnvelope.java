package io.github.temporalrift.game.shared;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;

/**
 * The {@code timeline.events} envelope metadata, read from Kafka record headers (event-schema.md §1) — not from a
 * body field. The record body carries only the typed event payload, so {@code eventType} here is the contract's
 * plain AsyncAPI message name and the sole routing discriminator.
 *
 * <p>Every header here travels as a plain String (TimelineEventHeaders.populate calls {@code .toString()} /
 * {@code String.valueOf()} on each value before putting it on the generated producer's header map) — none arrive as
 * their native type, so all of them must be parsed rather than cast.
 */
public record TimelineEventEnvelope(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        String aggregateType,
        UUID gameId,
        Instant occurredAt,
        Integer version) {

    private static final Logger log = LoggerFactory.getLogger(TimelineEventEnvelope.class);

    /** Null-safe check against a consumer's supported envelope version — an absent header is never supported. */
    public boolean hasVersion(int supported) {
        return version != null && version == supported;
    }

    /**
     * Checks the header {@code gameId} — which is also the record's partition key — against the game the payload
     * itself names. A mismatch means the record was routed for a different game than it describes, so acting on it
     * would mutate the payload's game off the wrong partition, outside that game's ordering guarantee. Callers
     * discard rather than process; an absent header {@code gameId} never matches.
     */
    public boolean matchesGameId(UUID payloadGameId) {
        if (gameId != null && gameId.equals(payloadGameId)) {
            return true;
        }
        log.warn(
                "{} event {} has payload gameId {} but envelope gameId {} — discarding",
                eventType,
                eventId,
                payloadGameId,
                gameId);
        return false;
    }

    public static TimelineEventEnvelope from(Message<?> message) {
        var headers = message.getHeaders();
        return new TimelineEventEnvelope(
                asUuid(headers.get("eventId", String.class)),
                headers.get("eventType", String.class),
                asUuid(headers.get("aggregateId", String.class)),
                headers.get("aggregateType", String.class),
                asUuid(headers.get("gameId", String.class)),
                asInstant(headers.get("occurredAt", String.class)),
                asInteger(headers.get("version", String.class)));
    }

    private static UUID asUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant asInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    /**
     * Yields null for an unparseable version rather than throwing. Silently truncating would turn an unsupported
     * envelope into the supported one, and the consumer would claim an event it cannot handle — the opposite of the
     * skip-without-claiming rule.
     */
    private static Integer asInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException _) {
            log.warn("Unparseable version header '{}' on timeline.events — treating as unsupported", value);
            return null;
        }
    }
}
