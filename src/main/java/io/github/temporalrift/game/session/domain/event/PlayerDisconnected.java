package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record PlayerDisconnected(UUID gameId, UUID playerId) {}
