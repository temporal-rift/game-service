package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record GameStarted(UUID gameId, UUID lobbyId, List<UUID> playerIds, int totalFactions, int deckSize) {}
