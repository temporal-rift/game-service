package io.github.temporalrift.game.action.domain.port.out;

import io.github.temporalrift.events.shared.CardType;

public interface BandRulesPort {

    /**
     * Probability shift applied by a probability-shifter card, or {@code 0} for cards that do not shift
     * probability during band calculation.
     */
    int cardShift(CardType cardType);

    /**
     * Magnitude of a Swing: applied as {@code -swingShift} to the source outcome and {@code +swingShift}
     * to the target outcome.
     */
    int swingShift();

    /** Inclusive upper bound (probability) for the LOW band. */
    int bandLowMaxProbability();

    /** Inclusive upper bound (probability) for the MEDIUM band; anything higher is HIGH. */
    int bandMediumMaxProbability();
}
