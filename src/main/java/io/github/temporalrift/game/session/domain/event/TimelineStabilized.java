package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record TimelineStabilized(UUID gameId, List<PlayerFactionResult> winners, List<PlayerFactionResult> losers) {

    public record PlayerFactionResult(UUID playerId, String faction, Integer activeChainLength) {}
}
