package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record FactionRevealed(UUID gameId, List<PlayerFactionResult> reveals) {

    public record PlayerFactionResult(UUID playerId, String faction) {}
}
