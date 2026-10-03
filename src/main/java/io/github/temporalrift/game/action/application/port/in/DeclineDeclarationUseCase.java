package io.github.temporalrift.game.action.application.port.in;

import java.util.UUID;

public interface DeclineDeclarationUseCase {
    /** Records a terminal decline, preserving the ordinary Round 1 action; accepted retries are idempotent. */
    Result handle(Command command);

    record Command(UUID gameId, int eraNumber, UUID playerId) {}

    record Result(UUID gameId, int eraNumber, UUID playerId) {}
}
