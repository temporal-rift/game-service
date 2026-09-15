package io.github.temporalrift.game.session.domain.port.out;

import java.util.Map;
import java.util.Set;

import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
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

    int handSelectionTimerSeconds(int playerCount);

    Map<CardCategory, Integer> cardCategoryWeights();

    Map<CardGrade, Integer> cardGradeWeights();

    Set<Faction> stabilizationWinnerFactions();

    /**
     * Card types that a deal must include, regardless of the configured category/grade weights. Empty in
     * production; exists so a test environment can eliminate the randomness of which card types a black-box
     * scenario happens to receive, without weakening or bypassing any real gameplay rule.
     */
    Set<CardType> handDealForcedTypes();
}
