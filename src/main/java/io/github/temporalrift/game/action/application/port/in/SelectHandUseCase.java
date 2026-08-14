package io.github.temporalrift.game.action.application.port.in;

import java.util.Set;
import java.util.UUID;

public interface SelectHandUseCase {
    Result handle(Command command);

    record Command(UUID gameId, int eraNumber, UUID playerId, Set<UUID> keptCardInstanceIds) {}

    record Result(UUID gameId, int eraNumber, UUID playerId) {}
}
