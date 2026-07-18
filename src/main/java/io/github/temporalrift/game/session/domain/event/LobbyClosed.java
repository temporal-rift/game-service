package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record LobbyClosed(UUID lobbyId, UUID gameId) {}
