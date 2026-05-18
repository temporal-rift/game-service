package io.github.temporalrift.game.action.domain.port.out;

public interface GameRulesPort {

    int actionRoundTimerSeconds(int playerCount);
}
