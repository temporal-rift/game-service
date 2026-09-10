package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

/** Private TRACE result containing only the viewer, target event, and distinct direct influencers. */
public record InfluenceTraced(
        UUID gameId, int eraNumber, int roundNumber, UUID playerId, UUID targetEventId, List<UUID> influencerPlayerIds)
        implements ActionEventPayload {

    public InfluenceTraced {
        influencerPlayerIds = List.copyOf(influencerPlayerIds);
    }
}
