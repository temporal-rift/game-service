package io.github.temporalrift.game.session.domain.saga;

import java.util.List;
import java.util.UUID;

public record EraSagaState(
        UUID gameId, int eraNumber, EraSagaStatus status, List<UUID> playerIds, List<UUID> handSelectedPlayerIds) {

    public EraSagaState(UUID gameId, int eraNumber, EraSagaStatus status, List<UUID> playerIds) {
        this(gameId, eraNumber, status, playerIds, List.of());
    }

    public EraSagaState withStatus(EraSagaStatus newStatus) {
        return new EraSagaState(gameId, eraNumber, newStatus, playerIds, handSelectedPlayerIds);
    }

    public EraSagaState markHandSelected(UUID playerId) {
        if (handSelectedPlayerIds.contains(playerId)) {
            return this;
        }
        var selected = new java.util.ArrayList<>(handSelectedPlayerIds);
        selected.add(playerId);
        return new EraSagaState(gameId, eraNumber, status, playerIds, List.copyOf(selected));
    }

    public boolean allHandsSelected() {
        return handSelectedPlayerIds.containsAll(playerIds);
    }
}
