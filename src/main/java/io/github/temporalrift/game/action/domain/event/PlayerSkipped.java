package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

public record PlayerSkipped(UUID gameId, int eraNumber, int roundNumber, UUID playerId, String reason) {}
