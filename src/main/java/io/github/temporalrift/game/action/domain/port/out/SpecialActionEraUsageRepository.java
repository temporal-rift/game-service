package io.github.temporalrift.game.action.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraUsage;

public interface SpecialActionEraUsageRepository {

    SpecialActionEraUsage save(SpecialActionEraUsage usage);

    Optional<SpecialActionEraUsage> findByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId);
}
