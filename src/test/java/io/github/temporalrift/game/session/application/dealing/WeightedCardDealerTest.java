package io.github.temporalrift.game.session.application.dealing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.CardCategory;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.HandDealt;

@ExtendWith(MockitoExtension.class)
class WeightedCardDealerTest {

    @Mock
    SessionGameRulesPort gameRules;

    @Test
    @DisplayName("a category with the only positive weight is selected for every dealt card")
    void deal_onlyPositiveCategoryWeight_dealsThatCategory() {
        // given
        given(gameRules.cardCategoryWeights()).willReturn(weights(CardCategory.PARADOX, 1));
        given(gameRules.cardGradeWeights()).willReturn(weights(CardGrade.I, 1));
        var dealer = new WeightedCardDealer(gameRules, new Random(42));

        // when
        var cards = dealer.deal(50);

        // then
        assertThat(cards).isNotEmpty();
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.cardType()).isEqualTo(CardType.COLLIDE);
            assertThat(card.grade()).isEqualTo(CardGrade.I);
        });
    }

    @Test
    @DisplayName("grade weights select only cards that support the selected grade")
    void deal_onlyGradeThreeWeight_dealsOnlyGradeThreeCapableCards() {
        // given
        given(gameRules.cardCategoryWeights()).willReturn(weights(CardCategory.INFORMATION, 1));
        given(gameRules.cardGradeWeights()).willReturn(weights(CardGrade.III, 1));
        var dealer = new WeightedCardDealer(gameRules, new Random(7));

        // when
        var cards = dealer.deal(50);

        // then
        assertThat(cards).isNotEmpty();
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.cardType()).isEqualTo(CardType.SCAN);
            assertThat(card.grade()).isEqualTo(CardGrade.III);
        });
    }

    @Test
    @DisplayName("categories without an ordinary card at an enabled grade are excluded before weighting")
    void deal_ineligibleHighWeightCategory_doesNotPreventEligibleCategoryDeal() {
        // given: Disruption has no grade-III ordinary card, while Probability Shifter does.
        given(gameRules.cardCategoryWeights())
                .willReturn(Map.of(
                        CardCategory.PROBABILITY_SHIFTER, 1,
                        CardCategory.INFORMATION, 0,
                        CardCategory.DISRUPTION, 100,
                        CardCategory.PARADOX, 0));
        given(gameRules.cardGradeWeights()).willReturn(weights(CardGrade.III, 1));
        var dealer = new WeightedCardDealer(gameRules, new Random(11));

        // when
        var cards = dealer.deal(50);

        // then
        assertThat(cards).isNotEmpty();
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.cardType().getCategory()).isEqualTo(CardCategory.PROBABILITY_SHIFTER);
            assertThat(card.grade()).isEqualTo(CardGrade.III);
        });
    }

    @Test
    @DisplayName("ordinary dealing never includes reactive Paradox Resolution cards")
    void deal_defaultWeights_neverDealsReactiveCards() {
        // given
        given(gameRules.cardCategoryWeights())
                .willReturn(Map.of(
                        CardCategory.PROBABILITY_SHIFTER, 35,
                        CardCategory.INFORMATION, 25,
                        CardCategory.DISRUPTION, 25,
                        CardCategory.PARADOX, 15));
        given(gameRules.cardGradeWeights()).willReturn(Map.of(CardGrade.I, 60, CardGrade.II, 30, CardGrade.III, 10));
        var dealer = new WeightedCardDealer(gameRules, new Random(99));

        // when
        var cards = dealer.deal(1_000);

        // then
        assertThat(cards)
                .extracting(HandDealt.CardInstance::cardType)
                .doesNotContain(CardType.STABILIZE, CardType.DETONATE);
        assertThat(cards)
                .allSatisfy(
                        card -> assertThat(card.cardType().supportedGrades()).contains(card.grade()));
    }

    @Test
    @DisplayName("configured category and grade weights drive a reproducible large sample")
    void deal_configuredWeights_followExpectedDistribution() {
        // Restrict to the fully graded probability-shifter group so the configured grade rarity is directly observable.
        given(gameRules.cardCategoryWeights()).willReturn(weights(CardCategory.PROBABILITY_SHIFTER, 1));
        given(gameRules.cardGradeWeights()).willReturn(Map.of(CardGrade.I, 60, CardGrade.II, 30, CardGrade.III, 10));
        var dealer = new WeightedCardDealer(gameRules, new Random(1234));

        var cards = dealer.deal(10_000);
        var grades = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        HandDealt.CardInstance::grade, java.util.stream.Collectors.counting()));

        assertThat(cards)
                .allSatisfy(
                        card -> assertThat(card.cardType().getCategory()).isEqualTo(CardCategory.PROBABILITY_SHIFTER));
        assertThat(grades.get(CardGrade.I)).isBetween(5_700L, 6_300L);
        assertThat(grades.get(CardGrade.II)).isBetween(2_700L, 3_300L);
        assertThat(grades.get(CardGrade.III)).isBetween(700L, 1_300L);
    }

    private static <T extends Enum<T>> Map<T, Integer> weights(T selected, int selectedWeight) {
        var weights = new EnumMap<T, Integer>(selected.getDeclaringClass());
        for (var value : selected.getDeclaringClass().getEnumConstants()) {
            weights.put(value, value == selected ? selectedWeight : 0);
        }
        return weights;
    }
}
