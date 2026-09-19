package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

class SessionRulesPropertiesTest {

    static SessionRulesProperties properties(Map<Integer, Integer> timers) {
        return new SessionRulesProperties(
                2,
                8,
                4,
                3,
                5,
                7,
                7,
                100,
                30,
                timers,
                timers,
                timers,
                Map.of(
                        CardCategory.PROBABILITY_SHIFTER, 35,
                        CardCategory.INFORMATION, 25,
                        CardCategory.DISRUPTION, 25,
                        CardCategory.PARADOX, 15),
                Map.of(CardGrade.I, 60, CardGrade.II, 30, CardGrade.III, 10),
                Set.of(Faction.PROPHETS, Faction.WEAVERS),
                Set.of(SpecialAction.ANNIHILATE, SpecialAction.SEAL, SpecialAction.CORRUPT, SpecialAction.MIMIC),
                2,
                Set.of());
    }

    @Test
    @DisplayName("actionRoundTimerSeconds returns the mapped value for a known player count")
    void actionRoundTimerSeconds_knownCount_returnsMappedValue() {
        // given
        var props = properties(Map.of(4, 45, 6, 90));

        // when / then
        assertThat(props.actionRoundTimerSeconds(4)).isEqualTo(45);
        assertThat(props.actionRoundTimerSeconds(6)).isEqualTo(90);
    }

    @Test
    @DisplayName("actionRoundTimerSeconds returns 60 for an unmapped player count")
    void actionRoundTimerSeconds_unknownCount_returnsDefault() {
        // given
        var props = properties(Map.of(4, 45));

        // when / then
        assertThat(props.actionRoundTimerSeconds(7)).isEqualTo(60);
    }

    @Test
    @DisplayName("handSelectionTimerSeconds returns the mapped value and falls back to 60")
    void handSelectionTimerSeconds_returnsMappedValueAndDefault() {
        // given
        var props = properties(Map.of(4, 45));

        // when / then
        assertThat(props.handSelectionTimerSeconds(4)).isEqualTo(45);
        assertThat(props.handSelectionTimerSeconds(7)).isEqualTo(60);
    }

    @Test
    void cardsPerDeal_lessThanCardsPerHand_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SessionRulesProperties(
                        2,
                        8,
                        4,
                        3,
                        5,
                        7,
                        5,
                        100,
                        30,
                        Map.of(3, 60),
                        Map.of(3, 60),
                        Map.of(3, 30),
                        Map.of(CardCategory.PARADOX, 1),
                        Map.of(CardGrade.I, 1),
                        Set.of(Faction.PROPHETS),
                        Set.of(SpecialAction.ANNIHILATE),
                        2,
                        Set.of()))
                .withMessage("cards-per-deal must be greater than or equal to cards-per-hand");
    }

    @Test
    void handDealForcedTypes_largerThanCardsPerDeal_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SessionRulesProperties(
                        2,
                        8,
                        4,
                        3,
                        5,
                        7,
                        7,
                        100,
                        30,
                        Map.of(3, 60),
                        Map.of(3, 60),
                        Map.of(3, 30),
                        Map.of(CardCategory.PARADOX, 1),
                        Map.of(CardGrade.I, 1),
                        Set.of(Faction.PROPHETS),
                        Set.of(SpecialAction.ANNIHILATE),
                        2,
                        Set.of(
                                CardType.TRACE,
                                CardType.NULLIFY,
                                CardType.DECOY,
                                CardType.STALL,
                                CardType.REDIRECT,
                                CardType.INTERCEPT,
                                CardType.SCAN,
                                CardType.COLLIDE)))
                .withMessage("hand-deal-forced-types must not exceed cards-per-deal");
    }

    @Test
    void declarationTimerSeconds_returnsMappedValueAndDefault() {
        assertThat(properties(Map.of(4, 45)).declarationTimerSeconds(4)).isEqualTo(45);
        assertThat(new SessionRulesProperties(
                                2,
                                8,
                                4,
                                3,
                                5,
                                7,
                                7,
                                100,
                                30,
                                Map.of(3, 60),
                                Map.of(3, 60),
                                null,
                                Map.of(CardCategory.PARADOX, 1),
                                Map.of(CardGrade.I, 1),
                                Set.of(Faction.PROPHETS),
                                Set.of(SpecialAction.ANNIHILATE),
                                2,
                                Set.of())
                        .declarationTimerSeconds(7))
                .isEqualTo(30);
    }

    @Test
    void nonPositiveHandSelectionTimer_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(Map.of(3, 0)))
                .withMessage("hand-selection-timer-seconds must contain only positive values");
    }

    @Test
    void nonPositiveDeclarationTimer_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SessionRulesProperties(
                        2,
                        8,
                        4,
                        3,
                        5,
                        7,
                        7,
                        100,
                        30,
                        Map.of(3, 60),
                        Map.of(3, 60),
                        Map.of(3, 0),
                        Map.of(CardCategory.PARADOX, 1),
                        Map.of(CardGrade.I, 1),
                        Set.of(Faction.PROPHETS),
                        Set.of(SpecialAction.ANNIHILATE),
                        2,
                        Set.of()))
                .withMessage("declaration-timer-seconds must contain only positive values");
    }
}
