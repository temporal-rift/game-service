package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record GameStartCancelled(UUID gameId, UUID lobbyId) {}
