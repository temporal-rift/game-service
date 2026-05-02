package io.github.temporalrift.game.session.domain.saga;

import java.util.List;
import java.util.UUID;

public record EndGameSagaState(
        UUID gameId, EndGameTrigger triggerType, EndGameSagaStatus status, List<UUID> playerIds) {

    public EndGameSagaState withStatus(EndGameSagaStatus newStatus) {
        return new EndGameSagaState(gameId, triggerType, newStatus, playerIds);
    }
}
