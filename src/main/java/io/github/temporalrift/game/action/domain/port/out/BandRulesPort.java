package io.github.temporalrift.game.action.domain.port.out;

import io.github.temporalrift.game.shared.CardGrade;

public interface BandRulesPort {

    /** Probability shift applied by a Push of the given grade. */
    int pushShift(CardGrade grade);

    /** Probability shift applied by a Suppress of the given grade (negative). */
    int suppressShift(CardGrade grade);

    /**
     * Magnitude of a Swing of the given grade: applied as {@code -swingShift} to the source outcome and
     * {@code +swingShift} to the target outcome.
     */
    int swingShift(CardGrade grade);

    /** Inclusive upper bound (probability) for the LOW band. */
    int bandLowMaxProbability();

    /** Inclusive upper bound (probability) for the MEDIUM band; anything higher is HIGH. */
    int bandMediumMaxProbability();
}
