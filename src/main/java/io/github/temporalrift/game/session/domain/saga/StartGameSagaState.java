package io.github.temporalrift.game.session.domain.saga;

import java.util.List;
import java.util.UUID;

public record StartGameSagaState(
        UUID sagaId,
        UUID gameId,
        UUID lobbyId,
        StartGameSagaStatus status,
        Integer currentStep,
        List<FactionAssignment> factionAssignments) {

    public StartGameSagaState withStatus(StartGameSagaStatus newStatus) {
        return new StartGameSagaState(sagaId, gameId, lobbyId, newStatus, currentStep, factionAssignments);
    }
}
