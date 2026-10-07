package io.github.temporalrift.game.session.application.dealing;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.event.HandDealt;
import io.github.temporalrift.game.shared.domain.model.CardDrawWeights;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;

/** Deals ordinary action cards by configured category and grade weights. */
@Component
public class WeightedCardDealer {

    private final SessionGameRulesPort gameRules;
    private final ExecutionEntropy entropy;

    public WeightedCardDealer(SessionGameRulesPort gameRules, ExecutionEntropy entropy) {
        this.gameRules = gameRules;
        this.entropy = entropy;
    }

    public List<HandDealt.CardInstance> deal(UUID playerId, int eraNumber, int cardCount) {
        var dealCoordinate = EntropyCoordinate.none().era(eraNumber).player(playerId);
        var random = entropy.generator(EntropyPurpose.CARD_DEAL, dealCoordinate);
        var forcedTypes = gameRules.handDealForcedTypes().stream()
                .sorted()
                .limit(cardCount)
                .toList();
        return IntStream.range(0, cardCount)
                .mapToObj(slot -> {
                    var cardType = slot < forcedTypes.size()
                            ? forcedTypes.get(slot)
                            : drawWeights().drawType(random);
                    return dealCardOfType(cardType, random, dealCoordinate.slot(slot));
                })
                .toList();
    }

    private CardDrawWeights drawWeights() {
        return new CardDrawWeights(gameRules.cardCategoryWeights(), gameRules.cardGradeWeights());
    }

    private HandDealt.CardInstance dealCardOfType(CardType cardType, RandomGenerator random, EntropyCoordinate slot) {
        var grade = drawWeights().drawGrade(cardType, random);
        return new HandDealt.CardInstance(entropy.identity(IdentityKind.CARD_INSTANCE, slot), cardType, grade);
    }
}
