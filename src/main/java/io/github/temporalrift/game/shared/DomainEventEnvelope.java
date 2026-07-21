package io.github.temporalrift.game.shared;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record DomainEventEnvelope<T>(
        UUID eventId, UUID aggregateId, String aggregateType, UUID gameId, Instant occurredAt, int version, T payload) {

    public static final int SCHEMA_VERSION_V1 = 1;

    public static <T> DomainEventEnvelope<T> create(
            UUID aggregateId, String aggregateType, UUID gameId, int version, T payload, Clock clock) {
        return new DomainEventEnvelope<>(
                UUID.randomUUID(), aggregateId, aggregateType, gameId, clock.instant(), version, payload);
    }
}
