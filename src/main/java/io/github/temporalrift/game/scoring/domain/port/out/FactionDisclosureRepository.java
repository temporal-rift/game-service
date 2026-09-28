package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * Scoring-owned record of players a public fact named as acting for their faction: an Activist declaration of
 * record, a revealed Expose, or a Weaver's public pending chain link. Identification is derived from these records.
 */
public interface FactionDisclosureRepository {

    void recordDisclosure(UUID gameId, UUID playerId);

    Set<UUID> disclosedPlayerIds(UUID gameId);
}
