package io.github.temporalrift.game.session.domain.port.out;

import io.github.temporalrift.game.shared.GameRulesPort;

public interface SessionGameRulesPort extends GameRulesPort {

    int minPlayers();

    int maxPlayers();

    int maxEras();

    int maxCascadedParadoxes();

    int eventsPerEra();

    int cardsPerHand();

    int winScoreThreshold();

    int reconnectGracePeriodSeconds();
}
