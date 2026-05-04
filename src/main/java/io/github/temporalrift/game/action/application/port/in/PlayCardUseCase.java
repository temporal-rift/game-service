package io.github.temporalrift.game.action.application.port.in;

import java.util.UUID;

import io.github.temporalrift.events.shared.CardType;

public interface PlayCardUseCase {

    Result handle(Command command);

    record Command(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            UUID targetEventId,
            UUID targetOutcomeId) {}

    record Result(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
