package io.github.temporalrift.game.shared;

import java.util.Set;

public interface GameRulesPort {

    int actionRoundTimerSeconds(int playerCount);

    /** Faction specials limited to one accepted use per player per era. */
    Set<SpecialAction> onceEraBudgetedSpecials();
}
