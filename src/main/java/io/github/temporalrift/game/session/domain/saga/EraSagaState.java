package io.github.temporalrift.game.session.domain.saga;

import java.util.List;
import java.util.UUID;

public record EraSagaState(UUID gameId, int eraNumber, EraSagaStatus status, List<UUID> playerIds) {

    public EraSagaState withStatus(EraSagaStatus newStatus) {
        return new EraSagaState(gameId, eraNumber, newStatus, playerIds);
    }
}
