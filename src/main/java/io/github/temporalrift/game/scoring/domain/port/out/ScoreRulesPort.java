package io.github.temporalrift.game.scoring.domain.port.out;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

public interface ScoreRulesPort {

    int pointsDelta(ScoreReason reason);

    /**
     * Whether erasing an event's written outcome at or after its writing scores nothing: no Eraser credit, and the
     * Prophet's writing and Fulfillment on that event expire.
     */
    boolean writtenOutcomeShieldsAnnihilation();
}
