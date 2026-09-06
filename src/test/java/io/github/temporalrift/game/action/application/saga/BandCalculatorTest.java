package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished.EventBandState;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished.OutcomeBandState;
import io.github.temporalrift.game.action.domain.port.out.BandRulesPort;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ProbabilityBand;
import io.github.temporalrift.game.shared.SpecialAction;

class BandCalculatorTest {

    static final UUID EVENT_ID = UUID.randomUUID();
    static final UUID OUTCOME_1 = UUID.randomUUID();
    static final UUID OUTCOME_2 = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    // Mirrors the GDD default per-grade balance values wired in application.yml (game.rules.probability).
    static final BandRulesPort GDD_BAND_RULES = new BandRulesPort() {
        @Override
        public int pushShift(CardGrade grade) {
            return switch (grade) {
                case I -> 10;
                case II -> 20;
                case III -> 30;
            };
        }

        @Override
        public int suppressShift(CardGrade grade) {
            return switch (grade) {
                case I -> -10;
                case II -> -20;
                case III -> -30;
            };
        }

        @Override
        public int swingShift(CardGrade grade) {
            return switch (grade) {
                case I -> 15;
                case II -> 30;
                case III -> 45;
            };
        }

        @Override
        public int bandLowMaxProbability() {
            return 30;
        }

        @Override
        public int bandMediumMaxProbability() {
            return 60;
        }
    };

    final BandCalculator calculator = new BandCalculator(GDD_BAND_RULES);

    @Test
    @DisplayName("computeBands applies card shifts cumulatively across both rounds")
    void computeBands_appliesCardShiftsAcrossRounds() {
        // given
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID,
                List.of(
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 20),
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_2, 55))));
        List<SubmittedAction> round1 = List.of(new SubmittedAction.CardAction(
                PLAYER_ID, UUID.randomUUID(), CardType.PUSH, CardGrade.II, EVENT_ID, null, OUTCOME_1));
        List<SubmittedAction> round2 = List.of(new SubmittedAction.CardAction(
                PLAYER_ID, UUID.randomUUID(), CardType.SUPPRESS, CardGrade.II, EVENT_ID, null, OUTCOME_2));

        // when
        var result = calculator.computeBands(round1, round2, definitions);

        // then
        assertThat(result).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(EVENT_ID);
            assertThat(event.outcomes())
                    .containsExactlyInAnyOrder(
                            new OutcomeBandState(OUTCOME_1, ProbabilityBand.MEDIUM),
                            new OutcomeBandState(OUTCOME_2, ProbabilityBand.MEDIUM));
        });
    }

    @Test
    @DisplayName("computeBands ignores actions that do not affect a tracked event outcome")
    void computeBands_ignoresUnsupportedActionsAndUnknownTargets() {
        // given
        var unknownEventId = UUID.randomUUID();
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 70))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), CardType.SCAN, EVENT_ID, null, OUTCOME_1),
                new SubmittedAction.CardAction(
                        PLAYER_ID, UUID.randomUUID(), CardType.PUSH, UUID.randomUUID(), null, OUTCOME_1));
        List<SubmittedAction> round2 = List.of(new SubmittedAction.SpecialActionSubmission(
                PLAYER_ID, Faction.ERASERS, SpecialAction.ANNIHILATE, unknownEventId, OUTCOME_1, null));

        // when
        var result = calculator.computeBands(round1, round2, definitions);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(event -> assertThat(event.outcomes())
                        .containsExactly(new OutcomeBandState(OUTCOME_1, ProbabilityBand.HIGH)));
    }

    @Test
    @DisplayName("computeBands uses GDD shift magnitudes for probability shifter cards")
    void computeBands_usesGddShiftMagnitudes() {
        // given
        var pushEventId = UUID.randomUUID();
        var suppressEventId = UUID.randomUUID();
        var swingEventId = UUID.randomUUID();
        var pushOutcomeId = UUID.randomUUID();
        var suppressOutcomeId = UUID.randomUUID();
        var swingSourceOutcomeId = UUID.randomUUID();
        var swingOutcomeId = UUID.randomUUID();
        var definitions = List.of(
                new FutureEventDefinitionPort.EventDefinition(
                        pushEventId, List.of(new FutureEventDefinitionPort.OutcomeDefinition(pushOutcomeId, 11))),
                new FutureEventDefinitionPort.EventDefinition(
                        suppressEventId,
                        List.of(new FutureEventDefinitionPort.OutcomeDefinition(suppressOutcomeId, 81))),
                new FutureEventDefinitionPort.EventDefinition(
                        swingEventId,
                        List.of(
                                new FutureEventDefinitionPort.OutcomeDefinition(swingSourceOutcomeId, 75),
                                new FutureEventDefinitionPort.OutcomeDefinition(swingOutcomeId, 31))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(
                        PLAYER_ID, UUID.randomUUID(), CardType.PUSH, CardGrade.II, pushEventId, null, pushOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID,
                        UUID.randomUUID(),
                        CardType.SUPPRESS,
                        CardGrade.II,
                        suppressEventId,
                        null,
                        suppressOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID,
                        UUID.randomUUID(),
                        CardType.SWING,
                        CardGrade.II,
                        swingEventId,
                        swingSourceOutcomeId,
                        swingOutcomeId));

        // when
        var result = calculator.computeBands(round1, List.of(), definitions);

        // then
        assertThat(result)
                .containsExactlyInAnyOrder(
                        new EventBandState(
                                pushEventId, List.of(new OutcomeBandState(pushOutcomeId, ProbabilityBand.MEDIUM))),
                        new EventBandState(
                                suppressEventId,
                                List.of(new OutcomeBandState(suppressOutcomeId, ProbabilityBand.HIGH))),
                        new EventBandState(
                                swingEventId,
                                List.of(
                                        new OutcomeBandState(swingSourceOutcomeId, ProbabilityBand.MEDIUM),
                                        new OutcomeBandState(swingOutcomeId, ProbabilityBand.HIGH))));
    }

    @Test
    @DisplayName("computeBands uses the grade I magnitude for PUSH/SUPPRESS/SWING, not the grade II baseline")
    void computeBands_usesGradeIShiftMagnitudes() {
        // given: each base probability crosses a band boundary only under the grade I magnitude, not grade II's.
        var pushEventId = UUID.randomUUID();
        var suppressEventId = UUID.randomUUID();
        var swingEventId = UUID.randomUUID();
        var pushOutcomeId = UUID.randomUUID();
        var suppressOutcomeId = UUID.randomUUID();
        var swingSourceOutcomeId = UUID.randomUUID();
        var swingOutcomeId = UUID.randomUUID();
        var definitions = List.of(
                // grade I: 15 + 10 = 25 (LOW); grade II would give 15 + 20 = 35 (MEDIUM).
                new FutureEventDefinitionPort.EventDefinition(
                        pushEventId, List.of(new FutureEventDefinitionPort.OutcomeDefinition(pushOutcomeId, 15))),
                // grade I: 45 - 10 = 35 (MEDIUM); grade II would give 45 - 20 = 25 (LOW).
                new FutureEventDefinitionPort.EventDefinition(
                        suppressEventId,
                        List.of(new FutureEventDefinitionPort.OutcomeDefinition(suppressOutcomeId, 45))),
                // grade I: source 46 - 15 = 31 (MEDIUM), target 15 + 15 = 30 (LOW);
                // grade II would give source 46 - 30 = 16 (LOW), target 15 + 30 = 45 (MEDIUM).
                new FutureEventDefinitionPort.EventDefinition(
                        swingEventId,
                        List.of(
                                new FutureEventDefinitionPort.OutcomeDefinition(swingSourceOutcomeId, 46),
                                new FutureEventDefinitionPort.OutcomeDefinition(swingOutcomeId, 15))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(
                        PLAYER_ID, UUID.randomUUID(), CardType.PUSH, CardGrade.I, pushEventId, null, pushOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID,
                        UUID.randomUUID(),
                        CardType.SUPPRESS,
                        CardGrade.I,
                        suppressEventId,
                        null,
                        suppressOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID,
                        UUID.randomUUID(),
                        CardType.SWING,
                        CardGrade.I,
                        swingEventId,
                        swingSourceOutcomeId,
                        swingOutcomeId));

        // when
        var result = calculator.computeBands(round1, List.of(), definitions);

        // then
        assertThat(result)
                .containsExactlyInAnyOrder(
                        new EventBandState(
                                pushEventId, List.of(new OutcomeBandState(pushOutcomeId, ProbabilityBand.LOW))),
                        new EventBandState(
                                suppressEventId,
                                List.of(new OutcomeBandState(suppressOutcomeId, ProbabilityBand.MEDIUM))),
                        new EventBandState(
                                swingEventId,
                                List.of(
                                        new OutcomeBandState(swingSourceOutcomeId, ProbabilityBand.MEDIUM),
                                        new OutcomeBandState(swingOutcomeId, ProbabilityBand.LOW))));
    }

    @Test
    @DisplayName("computeBands uses the grade III magnitude for PUSH/SUPPRESS/SWING, not the grade II baseline")
    void computeBands_usesGradeIiiShiftMagnitudes() {
        // given: each base probability crosses a band boundary only under the grade III magnitude, not grade II's.
        var pushEventId = UUID.randomUUID();
        var suppressEventId = UUID.randomUUID();
        var swingEventId = UUID.randomUUID();
        var pushOutcomeId = UUID.randomUUID();
        var suppressOutcomeId = UUID.randomUUID();
        var swingSourceOutcomeId = UUID.randomUUID();
        var swingOutcomeId = UUID.randomUUID();
        var definitions = List.of(
                // grade III: 35 + 30 = 65 (HIGH); grade II would give 35 + 20 = 55 (MEDIUM).
                new FutureEventDefinitionPort.EventDefinition(
                        pushEventId, List.of(new FutureEventDefinitionPort.OutcomeDefinition(pushOutcomeId, 35))),
                // grade III: 85 - 30 = 55 (MEDIUM); grade II would give 85 - 20 = 65 (HIGH).
                new FutureEventDefinitionPort.EventDefinition(
                        suppressEventId,
                        List.of(new FutureEventDefinitionPort.OutcomeDefinition(suppressOutcomeId, 85))),
                // grade III: source 95 - 45 = 50 (MEDIUM), target 20 + 45 = 65 (HIGH);
                // grade II would give source 95 - 30 = 65 (HIGH), target 20 + 30 = 50 (MEDIUM).
                new FutureEventDefinitionPort.EventDefinition(
                        swingEventId,
                        List.of(
                                new FutureEventDefinitionPort.OutcomeDefinition(swingSourceOutcomeId, 95),
                                new FutureEventDefinitionPort.OutcomeDefinition(swingOutcomeId, 20))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(
                        PLAYER_ID, UUID.randomUUID(), CardType.PUSH, CardGrade.III, pushEventId, null, pushOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID,
                        UUID.randomUUID(),
                        CardType.SUPPRESS,
                        CardGrade.III,
                        suppressEventId,
                        null,
                        suppressOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID,
                        UUID.randomUUID(),
                        CardType.SWING,
                        CardGrade.III,
                        swingEventId,
                        swingSourceOutcomeId,
                        swingOutcomeId));

        // when
        var result = calculator.computeBands(round1, List.of(), definitions);

        // then
        assertThat(result)
                .containsExactlyInAnyOrder(
                        new EventBandState(
                                pushEventId, List.of(new OutcomeBandState(pushOutcomeId, ProbabilityBand.HIGH))),
                        new EventBandState(
                                suppressEventId,
                                List.of(new OutcomeBandState(suppressOutcomeId, ProbabilityBand.MEDIUM))),
                        new EventBandState(
                                swingEventId,
                                List.of(
                                        new OutcomeBandState(swingSourceOutcomeId, ProbabilityBand.MEDIUM),
                                        new OutcomeBandState(swingOutcomeId, ProbabilityBand.HIGH))));
    }

    @Test
    @DisplayName("computeBands skips event definitions that disappear from the cumulative state")
    void computeBands_skipsDefinitionsMissingFromState() {
        // given
        var otherEventId = UUID.randomUUID();
        var definitions = List.of(
                new FutureEventDefinitionPort.EventDefinition(
                        EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 10))),
                new FutureEventDefinitionPort.EventDefinition(
                        otherEventId, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_2, 90))));

        // when
        var result = calculator.computeBands(List.of(), List.of(), definitions);

        // then
        assertThat(result).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(
            value = CardType.class,
            names = {
                "AMPLIFY",
                "INTERCEPT",
                "SCAN",
                "TRACE",
                "DECOY",
                "JAM",
                "STALL",
                "REDIRECT",
                "NULLIFY",
                "COLLIDE",
                "STABILIZE",
                "DETONATE"
            })
    @DisplayName("computeBands keeps probability unchanged for cards resolved outside band calculation")
    void computeBands_ignoresCardsResolvedOutsideBandCalculation(CardType cardType) {
        // given
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55))));
        List<SubmittedAction> actions = List.of(
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), cardType, EVENT_ID, null, OUTCOME_1));

        // when
        var result = calculator.computeBands(actions, List.of(), definitions);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(event -> assertThat(event.outcomes())
                        .containsExactly(new OutcomeBandState(OUTCOME_1, ProbabilityBand.MEDIUM)));
    }

    @ParameterizedTest
    @EnumSource(SpecialAction.class)
    @DisplayName("computeBands keeps probability unchanged for special actions resolved outside band calculation")
    void computeBands_ignoresSpecialActionsResolvedOutsideBandCalculation(SpecialAction specialAction) {
        // given
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55))));
        List<SubmittedAction> actions = List.of(new SubmittedAction.SpecialActionSubmission(
                PLAYER_ID, Faction.ERASERS, specialAction, EVENT_ID, OUTCOME_1, PLAYER_ID));

        // when
        var result = calculator.computeBands(List.of(), actions, definitions);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(event -> assertThat(event.outcomes())
                        .containsExactly(new OutcomeBandState(OUTCOME_1, ProbabilityBand.MEDIUM)));
    }

    @Test
    @DisplayName("computeBands ignores card and special actions without complete targets")
    void computeBands_ignoresActionsWithoutCompleteTargets() {
        // given
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), CardType.PUSH, null, null, OUTCOME_1),
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), CardType.PUSH, EVENT_ID, null, null));
        List<SubmittedAction> round2 = List.of(
                new SubmittedAction.SpecialActionSubmission(
                        PLAYER_ID, Faction.ERASERS, SpecialAction.SEAL, null, OUTCOME_1, null),
                new SubmittedAction.SpecialActionSubmission(
                        PLAYER_ID, Faction.ERASERS, SpecialAction.SEAL, EVENT_ID, null, null));

        // when
        var result = calculator.computeBands(round1, round2, definitions);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(event -> assertThat(event.outcomes())
                        .containsExactly(new OutcomeBandState(OUTCOME_1, ProbabilityBand.MEDIUM)));
    }

    @Test
    @DisplayName("computeBands rejects Swing when source outcome is missing")
    void computeBands_rejectsSwingWithoutSourceOutcome() {
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55))));
        List<SubmittedAction> actions = List.of(new SubmittedAction.CardAction(
                PLAYER_ID, UUID.randomUUID(), CardType.SWING, EVENT_ID, null, OUTCOME_1));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> calculator.computeBands(actions, List.of(), definitions));
    }

    @Test
    @DisplayName("computeBands rejects Swing when target outcome is missing")
    void computeBands_rejectsSwingWithoutTargetOutcome() {
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55))));
        List<SubmittedAction> actions = List.of(new SubmittedAction.CardAction(
                PLAYER_ID, UUID.randomUUID(), CardType.SWING, EVENT_ID, OUTCOME_1, null));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> calculator.computeBands(actions, List.of(), definitions));
    }

    @Test
    @DisplayName("computeBands rejects Swing when source and target outcomes match")
    void computeBands_rejectsSwingWithSameSourceAndTargetOutcome() {
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55))));
        List<SubmittedAction> actions = List.of(new SubmittedAction.CardAction(
                PLAYER_ID, UUID.randomUUID(), CardType.SWING, EVENT_ID, OUTCOME_1, OUTCOME_1));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> calculator.computeBands(actions, List.of(), definitions));
    }

    @Test
    @DisplayName("computeBands ignores Swing when source outcome is not tracked")
    void computeBands_ignoresSwingWithUnknownSourceOutcome() {
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID,
                List.of(
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55),
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_2, 55))));
        List<SubmittedAction> actions = List.of(new SubmittedAction.CardAction(
                PLAYER_ID, UUID.randomUUID(), CardType.SWING, EVENT_ID, UUID.randomUUID(), OUTCOME_2));

        var result = calculator.computeBands(actions, List.of(), definitions);

        assertThat(result)
                .singleElement()
                .satisfies(event -> assertThat(event.outcomes())
                        .containsExactlyInAnyOrder(
                                new OutcomeBandState(OUTCOME_1, ProbabilityBand.MEDIUM),
                                new OutcomeBandState(OUTCOME_2, ProbabilityBand.MEDIUM)));
    }

    @Test
    @DisplayName("computeBands ignores Swing when target outcome is not tracked")
    void computeBands_ignoresSwingWithUnknownTargetOutcome() {
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID,
                List.of(
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55),
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_2, 55))));
        List<SubmittedAction> actions = List.of(new SubmittedAction.CardAction(
                PLAYER_ID, UUID.randomUUID(), CardType.SWING, EVENT_ID, OUTCOME_1, UUID.randomUUID()));

        var result = calculator.computeBands(actions, List.of(), definitions);

        assertThat(result)
                .singleElement()
                .satisfies(event -> assertThat(event.outcomes())
                        .containsExactlyInAnyOrder(
                                new OutcomeBandState(OUTCOME_1, ProbabilityBand.MEDIUM),
                                new OutcomeBandState(OUTCOME_2, ProbabilityBand.MEDIUM)));
    }

    @Test
    @DisplayName("computeBands defaults a missing outcome probability to low")
    void computeBands_missingOutcomeProbability_defaultsToLowBand() {
        // given
        var definitions = List.of(
                new FutureEventDefinitionPort.EventDefinition(
                        EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 55))),
                new FutureEventDefinitionPort.EventDefinition(
                        EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_2, 55))));

        // when
        var result = calculator.computeBands(List.of(), List.of(), definitions);

        // then
        assertThat(result)
                .extracting(event -> event.outcomes().getFirst())
                .containsExactly(
                        new OutcomeBandState(OUTCOME_1, ProbabilityBand.LOW),
                        new OutcomeBandState(OUTCOME_2, ProbabilityBand.MEDIUM));
    }
}
