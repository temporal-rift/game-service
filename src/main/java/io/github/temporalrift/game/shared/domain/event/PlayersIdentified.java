package io.github.temporalrift.game.shared.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * In-process fact: at this round close a targeted private reveal (Intercept, Trace, Expose) landed on each listed
 * player while they were not obscured. Scoring records these as prior identifications; never published to Kafka.
 */
public record PlayersIdentified(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {

    public PlayersIdentified {
        playerIds = List.copyOf(playerIds);
    }
}
