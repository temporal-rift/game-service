package io.github.temporalrift.game.shared;

import java.time.Instant;
import java.util.UUID;

public record DomainEventEnvelope<T>(
        UUID eventId, UUID aggregateId, String aggregateType, UUID gameId, Instant occurredAt, int version, T payload) {

    public static <T> DomainEventEnvelope<T> create(
            UUID aggregateId, String aggregateType, UUID gameId, int version, T payload) {
        return new DomainEventEnvelope(
                UUID.randomUUID(), aggregateId, aggregateType, gameId, Instant.now(), version, payload);
    }
}
