package io.github.temporalrift.game.shared.domain.port.out;

import java.util.Set;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

public interface GameRulesPort {

    int actionRoundTimerSeconds(int playerCount);

    int declarationTimerSeconds(int playerCount);

    /** Faction specials limited to one accepted use per player per era. */
    Set<SpecialAction> onceEraBudgetedSpecials();

    /** Maximum accepted Seal uses per player per game. */
    int sealMaxUsesPerGame();

    /** Final era number; Stall promises a next era that does not exist at or beyond this era. */
    int maxEras();
}
