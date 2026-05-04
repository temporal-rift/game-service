package io.github.temporalrift.game.action.application.port.in;

import java.util.UUID;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;

public interface PlaySpecialActionUseCase {

    Result handle(Command command);

    record Command(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            Faction faction,
            SpecialAction specialAction,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {}

    record Result(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
