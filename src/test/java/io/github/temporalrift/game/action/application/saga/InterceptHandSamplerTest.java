package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

class InterceptHandSamplerTest {

    private static List<PlayerState.CardInstance> hand(PlayerState.CardInstance... cards) {
        return List.of(cards);
    }

    private static PlayerState.CardInstance card(CardType type) {
        return new PlayerState.CardInstance(UUID.randomUUID(), type, CardGrade.I);
    }

    @Test
    @DisplayName("empty hand selects nothing without failing")
    void emptyHand_selectsNothing() {
        assertThat(InterceptHandSampler.select(List.of(), CardGrade.I, new Random(1)))
                .isEmpty();
        assertThat(InterceptHandSampler.select(List.of(), CardGrade.II, new Random(1)))
                .isEmpty();
    }

    @Test
    @DisplayName("grade I selects exactly one card genuinely in the hand")
    void gradeOne_selectsOneCardFromHand() {
        var cards = hand(card(CardType.PUSH), card(CardType.SCAN), card(CardType.JAM));

        var selected = InterceptHandSampler.select(cards, CardGrade.I, new Random(7));

        assertThat(selected).hasSize(1);
        assertThat(cards).contains(selected.getFirst());
    }

    @Test
    @DisplayName("grade II selects two distinct cards from the hand")
    void gradeTwo_selectsTwoDistinctCards() {
        var cards = hand(card(CardType.PUSH), card(CardType.SCAN), card(CardType.JAM));

        var selected = InterceptHandSampler.select(cards, CardGrade.II, new Random(7));

        assertThat(selected).hasSize(2).doesNotHaveDuplicates();
        assertThat(cards).containsAll(selected);
    }

    @Test
    @DisplayName("grade II on a one-card hand reveals that card without error")
    void gradeTwoOnOneCardHand_revealsThatCard() {
        var only = card(CardType.SWING);

        var selected = InterceptHandSampler.select(hand(only), CardGrade.II, new Random(7));

        assertThat(selected).containsExactly(only);
    }

    @Test
    @DisplayName("selection shows no bias across repeated runs")
    void selection_isUniformAcrossRepeatedRuns() {
        var cards = hand(card(CardType.PUSH), card(CardType.SCAN), card(CardType.JAM));
        var randomness = new Random(42);
        var counts = new HashMap<UUID, Integer>();
        var draws = 3000;

        for (var i = 0; i < draws; i++) {
            var picked =
                    InterceptHandSampler.select(cards, CardGrade.I, randomness).getFirst();
            counts.merge(picked.cardInstanceId(), 1, Integer::sum);
        }

        assertThat(counts).hasSize(3);
        counts.values().forEach(count -> assertThat(count).isBetween(draws / 3 - 300, draws / 3 + 300));
    }
}
