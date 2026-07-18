package io.github.temporalrift.game.shared;

import java.time.Instant;
import java.util.UUID;

public record DomainEventEnvelope(
        UUID eventId,
        UUID aggregateId,
        String aggregateType,
        UUID gameId,
        Instant occurredAt,
        int version,
        Object payload) {

    public static DomainEventEnvelope create(
            UUID aggregateId, String aggregateType, UUID gameId, int version, Object payload) {
        return new DomainEventEnvelope(
                UUID.randomUUID(), aggregateId, aggregateType, gameId, Instant.now(), version, payload);
    }
}
