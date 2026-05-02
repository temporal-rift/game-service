package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

public record PlayerReconnectedApplicationEvent(UUID gameId, UUID playerId) {}
