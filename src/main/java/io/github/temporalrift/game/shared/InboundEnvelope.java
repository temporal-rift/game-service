package io.github.temporalrift.game.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization target for raw Kafka messages consumed from topics without a generated ZenWave
 * consumer (commands, and events from services - like timeline-service - that don't exist yet).
 */
public record InboundEnvelope(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        String aggregateType,
        UUID gameId,
        Instant occurredAt,
        int version,
        Object payload) {}
