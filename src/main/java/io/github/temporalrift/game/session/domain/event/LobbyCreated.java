package io.github.temporalrift.game.session.domain.event;

import java.time.Instant;
import java.util.UUID;

public record LobbyCreated(UUID lobbyId, UUID hostPlayerId, Instant createdAt) {}
