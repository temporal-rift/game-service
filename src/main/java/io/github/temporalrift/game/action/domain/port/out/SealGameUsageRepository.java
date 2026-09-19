package io.github.temporalrift.game.action.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.specialactionerausage.SealGameUsage;

public interface SealGameUsageRepository {

    SealGameUsage save(SealGameUsage usage);

    Optional<SealGameUsage> findByGameIdAndPlayerId(UUID gameId, UUID playerId);
}
