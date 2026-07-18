package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

public record ActionRoundClosed(UUID gameId, int eraNumber, int roundNumber, String closedReason, int totalActions) {}
