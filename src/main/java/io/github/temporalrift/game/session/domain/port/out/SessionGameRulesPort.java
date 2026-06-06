package io.github.temporalrift.game.session.domain.port.out;

public interface SessionGameRulesPort extends io.github.temporalrift.game.shared.GameRulesPort {

    int minPlayers();

    int maxPlayers();

    int maxEras();

    int maxCascadedParadoxes();

    int eventsPerEra();

    int cardsPerHand();

    int winScoreThreshold();

    int reconnectGracePeriodSeconds();
}
