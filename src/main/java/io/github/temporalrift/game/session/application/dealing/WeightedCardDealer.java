package io.github.temporalrift.game.session.application.dealing;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.event.HandDealt;
import io.github.temporalrift.game.shared.domain.model.CardDrawWeights;
import io.github.temporalrift.game.shared.domain.model.CardType;

/** Deals ordinary action cards by configured category and grade weights. */
@Component
public class WeightedCardDealer {

    private final SessionGameRulesPort gameRules;
    private final RandomGenerator random;

    public WeightedCardDealer(SessionGameRulesPort gameRules, RandomGenerator random) {
        this.gameRules = gameRules;
        this.random = random;
    }

    public List<HandDealt.CardInstance> deal(int cardCount) {
        var forcedTypes =
                gameRules.handDealForcedTypes().stream().limit(cardCount).toList();
        var forced = forcedTypes.stream().map(this::dealCardOfType);
        var remaining = IntStream.range(0, cardCount - forcedTypes.size()).mapToObj(ignored -> dealCard());
        return Stream.concat(forced, remaining).toList();
    }

    private CardDrawWeights drawWeights() {
        return new CardDrawWeights(gameRules.cardCategoryWeights(), gameRules.cardGradeWeights());
    }

    private HandDealt.CardInstance dealCard() {
        return dealCardOfType(drawWeights().drawType(random));
    }

    private HandDealt.CardInstance dealCardOfType(CardType cardType) {
        var grade = drawWeights().drawGrade(cardType, random);
        return new HandDealt.CardInstance(UUID.randomUUID(), cardType, grade);
    }
}
