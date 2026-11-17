package io.github.temporalrift.game.scoring.domain.port.out;

/**
 * The shared cap on a Prophet's writings per era. timeline-service applies the same value when it records writings,
 * so both services agree on the era's written outcomes.
 */
public interface ForesightRulesPort {

    /** How many outcomes a Prophet may write per era, earliest rounds first; at least 1. */
    int writingsPerEra();
}
