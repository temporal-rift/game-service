package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import java.util.Set;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import io.github.temporalrift.game.session.domain.ending.StabilizationThresholds;
import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
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
                120,
                timers,
                Map.of(
                        CardCategory.PROBABILITY_SHIFTER, 35,
                        CardCategory.INFORMATION, 25,
                        CardCategory.DISRUPTION, 25,
                        CardCategory.PARADOX, 15),
                Map.of(CardGrade.I, 60, CardGrade.II, 30, CardGrade.III, 10),
                new SessionRulesProperties.Stabilization(3, 2),
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
    @DisplayName("binding hand-selection-timer-seconds yields one duration for every player count")
    void bindingHandSelectionTimer_yieldsSingleDuration() {
        var source = minimalRules().withProperty("game.rules.hand-selection-timer-seconds", "120");

        assertThat(bindValidated(source).handSelectionTimerSeconds()).isEqualTo(120);
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
                        60,
                        Map.of(3, 30),
                        Map.of(CardCategory.PARADOX, 1),
                        Map.of(CardGrade.I, 1),
                        new SessionRulesProperties.Stabilization(3, 2),
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
                        60,
                        Map.of(3, 30),
                        Map.of(CardCategory.PARADOX, 1),
                        Map.of(CardGrade.I, 1),
                        new SessionRulesProperties.Stabilization(3, 2),
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
                                60,
                                null,
                                Map.of(CardCategory.PARADOX, 1),
                                Map.of(CardGrade.I, 1),
                                new SessionRulesProperties.Stabilization(3, 2),
                                Set.of(SpecialAction.ANNIHILATE),
                                2,
                                Set.of())
                        .declarationTimerSeconds(7))
                .isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    @DisplayName("a non-positive hand-selection-timer-seconds fails binding")
    void nonPositiveHandSelectionTimer_failsBinding(String seconds) {
        var source = minimalRules().withProperty("game.rules.hand-selection-timer-seconds", seconds);

        assertThatExceptionOfType(BindException.class).isThrownBy(() -> bindValidated(source));
    }

    @Test
    @DisplayName("a missing hand-selection-timer-seconds fails binding")
    void missingHandSelectionTimer_failsBinding() {
        var source = minimalRules();
        source.getSource().remove("game.rules.hand-selection-timer-seconds");

        assertThatExceptionOfType(BindException.class).isThrownBy(() -> bindValidated(source));
    }

    @Test
    @DisplayName("binding without seal-max-uses-per-game — defaults to two uses")
    void bindingWithoutSealMaxUsesPerGame_defaultsToTwo() {
        var source = minimalRules();
        var binder = new Binder(ConfigurationPropertySources.from(source));

        var bound = binder.bind("game.rules", SessionRulesProperties.class).orElseThrow(IllegalStateException::new);

        assertThat(bound.sealMaxUsesPerGame()).isEqualTo(2);
        assertThat(bound.stabilizationThresholds()).isEqualTo(new StabilizationThresholds(3, 2));
    }

    @Test
    @DisplayName("binding stabilization thresholds maps them to the domain thresholds")
    void bindingStabilizationThresholds_mapsToDomainThresholds() {
        var source = minimalRules()
                .withProperty("game.rules.stabilization.prophet-written-resolutions", "4")
                .withProperty("game.rules.stabilization.weaver-active-chain-links", "1");
        var binder = new Binder(ConfigurationPropertySources.from(source));

        var bound = binder.bind("game.rules", SessionRulesProperties.class).orElseThrow(IllegalStateException::new);

        assertThat(bound.stabilizationThresholds()).isEqualTo(new StabilizationThresholds(4, 1));
    }

    private static MockPropertySource minimalRules() {
        return new MockPropertySource()
                .withProperty("game.rules.min-players", "3")
                .withProperty("game.rules.max-players", "5")
                .withProperty("game.rules.max-eras", "5")
                .withProperty("game.rules.max-cascaded-paradoxes", "3")
                .withProperty("game.rules.events-per-era", "3")
                .withProperty("game.rules.cards-per-hand", "5")
                .withProperty("game.rules.cards-per-deal", "7")
                .withProperty("game.rules.win-score-threshold", "20")
                .withProperty("game.rules.reconnect-grace-period-seconds", "30")
                .withProperty("game.rules.action-round-timer-seconds.3", "60")
                .withProperty("game.rules.hand-selection-timer-seconds", "60")
                .withProperty("game.rules.card-category-weights.PROBABILITY_SHIFTER", "35")
                .withProperty("game.rules.card-category-weights.INFORMATION", "25")
                .withProperty("game.rules.card-category-weights.DISRUPTION", "25")
                .withProperty("game.rules.card-category-weights.PARADOX", "15")
                .withProperty("game.rules.card-grade-weights.I", "60")
                .withProperty("game.rules.card-grade-weights.II", "30")
                .withProperty("game.rules.card-grade-weights.III", "10")
                .withProperty("game.rules.once-era-budgeted-specials[0]", "SEAL");
    }

    private static SessionRulesProperties bindValidated(MockPropertySource source) {
        var validator = new SpringValidatorAdapter(
                Validation.buildDefaultValidatorFactory().getValidator());
        return new Binder(ConfigurationPropertySources.from(source))
                .bind("game.rules", Bindable.of(SessionRulesProperties.class), new ValidationBindHandler(validator))
                .orElseThrow(IllegalStateException::new);
    }

    @Test
    @DisplayName("a non-positive stabilization threshold fails binding")
    void nonPositiveStabilizationThreshold_failsBinding() {
        var source = minimalRules().withProperty("game.rules.stabilization.weaver-active-chain-links", "0");

        assertThatExceptionOfType(BindException.class).isThrownBy(() -> bindValidated(source));
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
                        60,
                        Map.of(3, 0),
                        Map.of(CardCategory.PARADOX, 1),
                        Map.of(CardGrade.I, 1),
                        new SessionRulesProperties.Stabilization(3, 2),
                        Set.of(SpecialAction.ANNIHILATE),
                        2,
                        Set.of()))
                .withMessage("declaration-timer-seconds must contain only positive values");
    }
}
