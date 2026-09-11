package io.github.temporalrift.game.action.application.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.shared.CardGrade;

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
        // INTERCEPT only supports grades I and II; a stray grade must never fail round close.
        var count = Math.min(hand.size(), grade == CardGrade.II ? 2 : 1);
        var shuffled = new ArrayList<>(hand);
        Collections.shuffle(shuffled, randomness);
        return List.copyOf(shuffled.subList(0, count));
    }
}
