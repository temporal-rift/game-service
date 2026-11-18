package io.github.temporalrift.game.session.domain.port.out;

/** Whether simultaneous winners are separated instead of sharing the victory. */
public interface VictoryTiebreakRulesPort {

    /**
     * Whether the highest total among simultaneous normal-victory qualifiers wins alone, and a tied highest total at a
     * special ending is separated by faction-objective progress; players still tied share.
     */
    boolean scoreTiebreak();
}
