package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record PlayerJoinedLobby(UUID lobbyId, UUID playerId, String playerName) {}
