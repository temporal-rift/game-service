package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

record PlayerDisconnectedApplicationEvent(UUID gameId, UUID playerId) {}
