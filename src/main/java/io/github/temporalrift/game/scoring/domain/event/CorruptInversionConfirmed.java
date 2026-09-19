package io.github.temporalrift.game.scoring.domain.event;

import java.util.UUID;

public record CorruptInversionConfirmed(
        UUID gameId,
        int eraNumber,
        UUID corruptingPlayerId,
        UUID targetEventId,
        UUID targetOutcomeId,
        boolean tookEffect) {}
