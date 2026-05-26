package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.events.timeline.BandedProbabilityPublished.ProbabilityBand;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

class BandCalculatorTest {

    static final UUID EVENT_ID = UUID.randomUUID();
    static final UUID OUTCOME_1 = UUID.randomUUID();
    static final UUID OUTCOME_2 = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    final BandCalculator calculator = new BandCalculator();

    @Test
    @DisplayName("computeBands applies card shifts cumulatively across both rounds")
    void computeBands_appliesCardShiftsAcrossRounds() {
        // given
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID,
                List.of(
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 20),
                        new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_2, 55))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), CardType.PUSH, EVENT_ID, OUTCOME_1));
        List<SubmittedAction> round2 = List.of(
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), CardType.SUPPRESS, EVENT_ID, OUTCOME_2));

        // when
        var result = calculator.computeBands(round1, round2, definitions);

        // then
        assertThat(result).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(EVENT_ID);
            assertThat(event.outcomes())
                    .containsExactlyInAnyOrder(
                            new io.github.temporalrift.events.timeline.BandedProbabilityPublished.OutcomeBandState(
                                    OUTCOME_1, ProbabilityBand.MEDIUM),
                            new io.github.temporalrift.events.timeline.BandedProbabilityPublished.OutcomeBandState(
                                    OUTCOME_2, ProbabilityBand.MEDIUM));
        });
    }

    @Test
    @DisplayName("computeBands ignores actions that do not affect a tracked event outcome")
    void computeBands_ignoresUnsupportedActionsAndUnknownTargets() {
        // given
        var definitions = List.of(new FutureEventDefinitionPort.EventDefinition(
                EVENT_ID, List.of(new FutureEventDefinitionPort.OutcomeDefinition(OUTCOME_1, 70))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), CardType.SCAN, EVENT_ID, OUTCOME_1),
                new SubmittedAction.CardAction(
                        PLAYER_ID, UUID.randomUUID(), CardType.PUSH, UUID.randomUUID(), OUTCOME_1));
        List<SubmittedAction> round2 = List.of(new SubmittedAction.SpecialActionSubmission(
                PLAYER_ID, Faction.ERASERS, SpecialAction.ANNIHILATE, EVENT_ID, OUTCOME_1, null));

        // when
        var result = calculator.computeBands(round1, round2, definitions);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(event -> assertThat(event.outcomes())
                        .containsExactly(
                                new io.github.temporalrift.events.timeline.BandedProbabilityPublished.OutcomeBandState(
                                        OUTCOME_1, ProbabilityBand.HIGH)));
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
        var swingOutcomeId = UUID.randomUUID();
        var definitions = List.of(
                new FutureEventDefinitionPort.EventDefinition(
                        pushEventId, List.of(new FutureEventDefinitionPort.OutcomeDefinition(pushOutcomeId, 11))),
                new FutureEventDefinitionPort.EventDefinition(
                        suppressEventId,
                        List.of(new FutureEventDefinitionPort.OutcomeDefinition(suppressOutcomeId, 81))),
                new FutureEventDefinitionPort.EventDefinition(
                        swingEventId, List.of(new FutureEventDefinitionPort.OutcomeDefinition(swingOutcomeId, 31))));
        List<SubmittedAction> round1 = List.of(
                new SubmittedAction.CardAction(PLAYER_ID, UUID.randomUUID(), CardType.PUSH, pushEventId, pushOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID, UUID.randomUUID(), CardType.SUPPRESS, suppressEventId, suppressOutcomeId),
                new SubmittedAction.CardAction(
                        PLAYER_ID, UUID.randomUUID(), CardType.SWING, swingEventId, swingOutcomeId));

        // when
        var result = calculator.computeBands(round1, List.of(), definitions);

        // then
        assertThat(result)
                .containsExactlyInAnyOrder(
                        new io.github.temporalrift.events.timeline.BandedProbabilityPublished.EventBandState(
                                pushEventId,
                                List.of(
                                        new io.github.temporalrift.events.timeline.BandedProbabilityPublished
                                                .OutcomeBandState(pushOutcomeId, ProbabilityBand.MEDIUM))),
                        new io.github.temporalrift.events.timeline.BandedProbabilityPublished.EventBandState(
                                suppressEventId,
                                List.of(
                                        new io.github.temporalrift.events.timeline.BandedProbabilityPublished
                                                .OutcomeBandState(suppressOutcomeId, ProbabilityBand.HIGH))),
                        new io.github.temporalrift.events.timeline.BandedProbabilityPublished.EventBandState(
                                swingEventId,
                                List.of(
                                        new io.github.temporalrift.events.timeline.BandedProbabilityPublished
                                                .OutcomeBandState(swingOutcomeId, ProbabilityBand.HIGH))));
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
}
