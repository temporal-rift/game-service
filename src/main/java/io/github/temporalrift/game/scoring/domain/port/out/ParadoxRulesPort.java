package io.github.temporalrift.game.scoring.domain.port.out;

/** The paradox resolution rules that change how a cascade is scored. */
public interface ParadoxRulesPort {

    /**
     * Whether Detonate contests Stabilize. When it does, a cascade costs every player the base penalty and each
     * detonator an extra {@code PARADOX_DETONATED}; when it does not, a Detonate doubles the penalty for the others.
     */
    boolean detonateContestsStabilize();
}
