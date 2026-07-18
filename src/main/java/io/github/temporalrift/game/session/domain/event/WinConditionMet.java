package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record WinConditionMet(UUID gameId, UUID winnerId, String faction, int finalScore, String winType) {}
