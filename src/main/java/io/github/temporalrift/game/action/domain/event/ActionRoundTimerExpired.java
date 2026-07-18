package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

public record ActionRoundTimerExpired(UUID gameId, int eraNumber, int roundNumber, List<UUID> missingPlayerIds) {}
