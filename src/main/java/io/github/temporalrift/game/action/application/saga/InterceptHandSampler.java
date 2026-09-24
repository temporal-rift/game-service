package io.github.temporalrift.game.action.application.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.shared.domain.model.CardDrawWeights;
import io.github.temporalrift.game.shared.domain.model.CardGrade;

/**
 * Uniformly samples distinct cards from a target hand for INTERCEPT resolution. Grade I
 * reveals one card, grade II reveals two; a hand smaller than the grade count yields
 * whatever it holds (possibly nothing) rather than an error.
 */
final class InterceptHandSampler {

    private InterceptHandSampler() {}

    static List<PlayerState.CardInstance> select(
            List<PlayerState.CardInstance> hand, CardGrade grade, Random randomness) {
        if (hand.isEmpty()) {
            return List.of();
        }
        var shuffled = new ArrayList<>(hand);
        Collections.shuffle(shuffled, randomness);
        return List.copyOf(shuffled.subList(0, revealCount(hand, grade)));
    }

    /**
     * Freshly drawn cards standing in for an obscured target's hand, as many as {@link #select} would reveal so
     * the result cannot be told apart by size or by matching instance ids.
     */
    static List<PlayerState.CardInstance> decoys(
            List<PlayerState.CardInstance> hand, CardGrade grade, CardDrawWeights weights, Random randomness) {
        return IntStream.range(0, revealCount(hand, grade))
                .mapToObj(ignored -> {
                    var cardType = weights.drawType(randomness);
                    return new PlayerState.CardInstance(
                            UUID.randomUUID(), cardType, weights.drawGrade(cardType, randomness));
                })
                .toList();
    }

    // INTERCEPT only supports grades I and II; a stray grade must never fail round close.
    private static int revealCount(List<PlayerState.CardInstance> hand, CardGrade grade) {
        return Math.min(hand.size(), grade == CardGrade.II ? 2 : 1);
    }
}
