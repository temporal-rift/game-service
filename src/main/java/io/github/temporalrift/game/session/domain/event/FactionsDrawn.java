package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record FactionsDrawn(UUID gameId, UUID lobbyId, List<String> factions) {}
