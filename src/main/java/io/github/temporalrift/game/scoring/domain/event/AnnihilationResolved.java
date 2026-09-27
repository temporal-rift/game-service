package io.github.temporalrift.game.scoring.domain.event;

import java.util.UUID;

public record AnnihilationResolved(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID annihilatingPlayerId,
        UUID targetEventId,
        UUID targetOutcomeId,
        boolean erased,
        boolean wasLeading) {}
