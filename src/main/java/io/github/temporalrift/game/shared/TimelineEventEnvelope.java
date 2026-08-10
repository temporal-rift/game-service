package io.github.temporalrift.game.shared;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

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

    /** Null-safe check against a consumer's supported envelope version — an absent header is never supported. */
    public boolean hasVersion(int supported) {
        return version != null && version == supported;
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

    private static Integer asInteger(Object value) {
        return switch (value) {
            case Number number -> number.intValue();
            case null -> null;
            default -> {
                var text = asString(value);
                yield text == null ? null : Integer.valueOf(text.trim());
            }
        };
    }
}
