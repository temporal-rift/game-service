package io.github.temporalrift.game.shared.domain.port.out;

import java.util.Set;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

public interface GameRulesPort {

    int actionRoundTimerSeconds(int playerCount);

    /** Faction specials limited to one accepted use per player per era. */
    Set<SpecialAction> onceEraBudgetedSpecials();
}
