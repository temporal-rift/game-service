package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record FactionAssigned(UUID gameId, UUID playerId, String faction) {}
