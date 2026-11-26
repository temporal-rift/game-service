package io.github.temporalrift.game.session.domain.port.out;

/** The session's part of the victory rules: whether normal victory exists and how simultaneous winners separate. */
public interface SessionVictoryRulesPort {

    /**
     * Whether the highest total among simultaneous normal-victory qualifiers wins alone, and a tied highest total at a
     * special ending is separated by faction-objective progress; players still tied share.
     */
    boolean scoreTiebreak();

    /**
     * Whether reaching the score threshold or meeting a faction objective ends the game. When off, no player ever
     * qualifies for normal victory: every era is played and the game ends at the end of the arc or by collapse.
     */
    boolean normalVictory();
}
