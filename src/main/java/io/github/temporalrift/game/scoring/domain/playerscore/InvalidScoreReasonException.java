package io.github.temporalrift.game.scoring.domain.playerscore;

import io.github.temporalrift.events.shared.Faction;

public class InvalidScoreReasonException extends RuntimeException {

    public InvalidScoreReasonException(Faction playerFaction, ScoreReason reason) {
        super("Score reason %s belongs to %s, not %s".formatted(reason, reason.faction(), playerFaction));
    }
}
