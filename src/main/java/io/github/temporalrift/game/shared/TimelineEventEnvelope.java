package io.github.temporalrift.game.shared;

import java.nio.charset.StandardCharsets;
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
 * <p>Producers stamp the UUID fields as plain strings and {@code occurredAt}/{@code version} as their own types, but
 * the header mapper only reconstructs non-String types for trusted packages — every field is therefore read
 * defensively, tolerating the typed value, its {@code String} rendering, and the raw {@code byte[]} the mapper
 * delivers when it does not trust the type.
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
        return new TimelineEventEnvelope(
                asUuid(header(message, "eventId")),
                asString(header(message, "eventType")),
                asUuid(header(message, "aggregateId")),
                asString(header(message, "aggregateType")),
                asUuid(header(message, "gameId")),
                asInstant(header(message, "occurredAt")),
                asInteger(header(message, "version")));
    }

    private static Object header(Message<?> message, String name) {
        return message.getHeaders().get(name);
    }

    private static String asString(Object value) {
        return switch (value) {
            case String text -> text;
            case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
            case null, default -> null;
        };
    }

    private static UUID asUuid(Object value) {
        var text = asString(value);
        return text == null ? null : UUID.fromString(text);
    }

    private static Instant asInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case null -> null;
            default -> {
                var text = asString(value);
                // A JSON-serialized Instant header arrives quoted; strip the quotes before parsing.
                yield text == null ? null : Instant.parse(text.replace("\"", ""));
            }
        };
    }

    /**
     * Yields null for anything that is not exactly an {@code int}. Truncating a fractional, out-of-range, or
     * unparseable version would silently turn an unsupported envelope into the supported one, and the consumer
     * would claim an event it cannot handle — the opposite of the skip-without-claiming rule.
     */
    private static Integer asInteger(Object value) {
        return switch (value) {
            case Integer version -> version;
            case Number number -> isExactInt(number) ? number.intValue() : null;
            case null -> null;
            default -> parseInt(asString(value));
        };
    }

    private static boolean isExactInt(Number number) {
        var asLong = number.longValue();
        return asLong == (int) asLong && number.doubleValue() == asLong;
    }

    private static Integer parseInt(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException _) {
            log.warn("Unparseable version header '{}' on timeline.events — treating as unsupported", text);
            return null;
        }
    }
}
