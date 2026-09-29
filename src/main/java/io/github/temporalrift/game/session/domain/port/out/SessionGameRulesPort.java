package io.github.temporalrift.game.session.domain.port.out;

import java.util.Set;

import io.github.temporalrift.game.session.domain.ending.StabilizationThresholds;
import io.github.temporalrift.game.session.domain.futureevent.ProbabilityBounds;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

public interface SessionGameRulesPort extends GameRulesPort {

    int minPlayers();

    int maxPlayers();

    int maxEras();

    int maxCascadedParadoxes();

    int eventsPerEra();

    int cardsPerHand();

    int cardsPerDeal();

    int winScoreThreshold();

    int reconnectGracePeriodSeconds();

    int handSelectionTimerSeconds();

    StabilizationThresholds stabilizationThresholds();

    /** The probability floor and ceiling timeline enforces, against which catalog cards are validated. */
    ProbabilityBounds probabilityBounds();

    /**
     * Card types that a deal must include, regardless of the configured category/grade weights. Empty in
     * production; exists so a test environment can eliminate the randomness of which card types a black-box
     * scenario happens to receive, without weakening or bypassing any real gameplay rule.
     */
    Set<CardType> handDealForcedTypes();
}
