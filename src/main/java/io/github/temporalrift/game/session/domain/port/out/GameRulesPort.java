package io.github.temporalrift.game.session.domain.port.out;

public interface GameRulesPort {

    int minPlayers();

    int maxPlayers();

    int maxEras();

    int maxCascadedParadoxes();

    int eventsPerEra();

    int actionRoundTimerSeconds(int playerCount);

    int cardsPerHand();

    int winScoreThreshold();
}
