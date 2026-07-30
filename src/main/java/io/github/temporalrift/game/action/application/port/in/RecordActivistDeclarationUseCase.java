package io.github.temporalrift.game.action.application.port.in;

import java.util.UUID;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;

/** Records an Activist's sole declaration of record for the current era. */
public interface RecordActivistDeclarationUseCase {

    Result handle(Command command);

    record Command(
            UUID gameId,
            int eraNumber,
            UUID playerId,
            ActivistDeclarationMode mode,
            UUID targetEventId,
            UUID targetOutcomeId) {}

    record Result(
            UUID gameId,
            int eraNumber,
            UUID playerId,
            ActivistDeclarationMode mode,
            UUID targetEventId,
            UUID targetOutcomeId) {}
}
