package io.github.temporalrift.game.action.application.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.shared.domain.model.CardDrawWeights;
import io.github.temporalrift.game.shared.domain.model.CardGrade;

/**
 * Uniformly samples distinct cards from a target hand for INTERCEPT resolution. Grade I
 * reveals one card, grade II reveals two; a hand smaller than the grade count yields
 * whatever it holds (possibly nothing) rather than an error. An obscured target is sampled
 * the same way from its {@link #decoyHand}.
 */
final class InterceptHandSampler {

    private InterceptHandSampler() {}

    static List<PlayerState.CardInstance> select(
            List<PlayerState.CardInstance> hand, CardGrade grade, RandomGenerator randomness) {
        if (hand.isEmpty()) {
            return List.of();
        }
        var shuffled = new ArrayList<>(hand);
        Collections.shuffle(shuffled, randomness);
        return List.copyOf(shuffled.subList(0, revealCount(hand, grade)));
    }

    /**
     * The hand an obscured target shows every Intercept resolving at one round close: the cards already revealed
     * from its real hand this era, topped up with fresh draws to the real hand's size, so it reveals nothing new.
     */
    static List<PlayerState.CardInstance> decoyHand(
            PlayerState target, CardDrawWeights weights, RandomGenerator randomness, IntFunction<UUID> freshCardId) {
        var revealed = target.revealedCards();
        var fresh = IntStream.range(0, target.hand().size() - revealed.size()).mapToObj(slot -> {
            var cardType = weights.drawType(randomness);
            return new PlayerState.CardInstance(
                    freshCardId.apply(slot), cardType, weights.drawGrade(cardType, randomness));
        });
        return Stream.concat(revealed.stream(), fresh).toList();
    }

    // INTERCEPT only supports grades I and II; a stray grade must never fail round close.
    private static int revealCount(List<PlayerState.CardInstance> hand, CardGrade grade) {
        return Math.min(hand.size(), grade == CardGrade.II ? 2 : 1);
    }
}
