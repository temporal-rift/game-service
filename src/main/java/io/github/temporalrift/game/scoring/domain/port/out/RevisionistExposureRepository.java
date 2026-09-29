package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.Set;
import java.util.UUID;

/** Scoring-owned record of Revisionists whose Mimic another player's Trace revealed. */
public interface RevisionistExposureRepository {

    void recordExposure(UUID gameId, UUID playerId);

    Set<UUID> exposedPlayerIds(UUID gameId);
}
