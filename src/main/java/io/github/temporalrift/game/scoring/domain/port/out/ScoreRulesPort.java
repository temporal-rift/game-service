package io.github.temporalrift.game.scoring.domain.port.out;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

public interface ScoreRulesPort {

    int pointsDelta(ScoreReason reason);

    /**
     * Whether erasing an event's written outcome at or after its writing scores nothing: no Eraser credit, and the
     * Prophet's writing and Fulfillment on that event expire.
     */
    boolean writtenOutcomeShieldsAnnihilation();

    /**
     * Whether an Expose scores only when its revealed signature's event is the event named by the Activist's own
     * recorded declaration of the era.
     */
    boolean exposeRequiresDeclaredEvent();

    /**
     * Whether an Annihilate earns {@code ANNIHILATED_OUTCOME}, and its objective step, only when its event's outcome
     * reports that the erasure changed the winner.
     */
    boolean annihilationRequiresConsequence();
}
