package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record GameStartFailed(UUID gameId, UUID lobbyId, String reason) {}
