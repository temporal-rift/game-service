package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Private TRACE result containing only the viewer, target event, distinct direct influencers, and the subset of
 * those influencers whose influence was a Mimic.
 */
public record InfluenceTraced(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID playerId,
        UUID targetEventId,
        List<UUID> influencerPlayerIds,
        List<UUID> mimicInfluencerPlayerIds)
        implements ActionEventPayload {

    public InfluenceTraced {
        influencerPlayerIds = List.copyOf(influencerPlayerIds);
        mimicInfluencerPlayerIds = List.copyOf(mimicInfluencerPlayerIds);
    }
}
