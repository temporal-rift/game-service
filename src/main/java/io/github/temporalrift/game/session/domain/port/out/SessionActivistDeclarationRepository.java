package io.github.temporalrift.game.session.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.SpecialAction;

public interface SessionActivistDeclarationRepository {

    void saveIfAbsent(UUID gameId, int eraNumber, UUID playerId, UUID targetEventId, SpecialAction mode);

    List<UUID> findPlayerIdsTargeting(UUID gameId, int eraNumber, UUID targetEventId);
}
