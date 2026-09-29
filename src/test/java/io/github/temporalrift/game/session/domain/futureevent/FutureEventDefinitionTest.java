package io.github.temporalrift.game.session.domain.futureevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition.OutcomeDefinition;

class FutureEventDefinitionTest {

    static OutcomeDefinition outcome(int probability) {
        return new OutcomeDefinition(UUID.randomUUID(), "description", probability);
    }

    static List<OutcomeDefinition> balancedOutcomes() {
        return List.of(outcome(33), outcome(33), outcome(34));
    }

    // --- FutureEventDefinition constructor ---

    @Test
    @DisplayName("Given valid inputs, constructor succeeds")
    void constructor_validInputs_succeeds() {
        // given / when / then
        var event = new FutureEventDefinition(UUID.randomUUID(), "The Collapse", balancedOutcomes());
        assertThat(event.outcomes()).hasSize(3);
    }

    @Test
    @DisplayName("Given null eventId, constructor throws NullPointerException")
    void constructor_nullEventId_throws() {
        var outcomes = balancedOutcomes();
        assertThatNullPointerException().isThrownBy(() -> new FutureEventDefinition(null, "title", outcomes));
    }

    @Test
    @DisplayName("Given null title, constructor throws NullPointerException")
    void constructor_nullTitle_throws() {
        var id = UUID.randomUUID();
        var outcomes = balancedOutcomes();
        assertThatNullPointerException().isThrownBy(() -> new FutureEventDefinition(id, null, outcomes));
    }

    @Test
    @DisplayName("Given null outcomes, constructor throws NullPointerException")
    void constructor_nullOutcomes_throws() {
        var id = UUID.randomUUID();
        assertThatNullPointerException().isThrownBy(() -> new FutureEventDefinition(id, "title", null));
    }

    @Test
    @DisplayName("Given two outcomes, constructor throws IllegalArgumentException")
    void constructor_twoOutcomes_throws() {
        var id = UUID.randomUUID();
        var outcomes = List.of(outcome(50), outcome(50));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FutureEventDefinition(id, "title", outcomes));
    }

    @Test
    @DisplayName("Given four outcomes, constructor throws IllegalArgumentException")
    void constructor_fourOutcomes_throws() {
        var id = UUID.randomUUID();
        var outcomes = List.of(outcome(25), outcome(25), outcome(25), outcome(25));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FutureEventDefinition(id, "title", outcomes));
    }

    @Test
    @DisplayName("Given probabilities summing to 99, constructor throws IllegalArgumentException")
    void constructor_probabilitiesNotSummingTo100_throws() {
        var id = UUID.randomUUID();
        var outcomes = List.of(outcome(33), outcome(33), outcome(33));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FutureEventDefinition(id, "title", outcomes));
    }

    @Test
    @DisplayName("Outcomes list is unmodifiable after construction")
    void constructor_outcomesListIsUnmodifiable() {
        // given
        var event = new FutureEventDefinition(UUID.randomUUID(), "title", balancedOutcomes());
        var extraOutcome = outcome(10);
        var outcomes = event.outcomes();

        // when / then
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> outcomes.add(extraOutcome));
    }

    @Test
    @DisplayName("Given a repeated outcome ID, constructor throws IllegalArgumentException")
    void constructor_duplicateOutcomeId_throws() {
        var id = UUID.randomUUID();
        var repeated = UUID.randomUUID();
        var outcomes = List.of(
                new OutcomeDefinition(repeated, "a", 33),
                new OutcomeDefinition(repeated, "b", 33),
                new OutcomeDefinition(UUID.randomUUID(), "c", 34));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FutureEventDefinition(id, "title", outcomes))
                .withMessage("Outcome IDs must be distinct");
    }

    // --- requireStartWithin ---

    private static final ProbabilityBounds BOUNDS = new ProbabilityBounds(0, 90);

    @Test
    @DisplayName("Given a distinct printed start within bounds, requireStartWithin accepts it")
    void requireStartWithin_distinctStartWithinBounds_accepts() {
        var event =
                new FutureEventDefinition(UUID.randomUUID(), "title", List.of(outcome(60), outcome(25), outcome(15)));

        assertThatCode(() -> event.requireStartWithin(BOUNDS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given a printed weight above the ceiling, requireStartWithin names the card")
    void requireStartWithin_weightAboveCeiling_throws() {
        var event = new FutureEventDefinition(UUID.randomUUID(), "title", List.of(outcome(92), outcome(5), outcome(3)));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> event.requireStartWithin(BOUNDS))
                .withMessageContaining(event.eventId().toString())
                .withMessageContaining("92");
    }

    @Test
    @DisplayName("Given a zero printed weight, requireStartWithin rejects the unwinnable outcome")
    void requireStartWithin_zeroWeight_throws() {
        var event =
                new FutureEventDefinition(UUID.randomUUID(), "title", List.of(outcome(50), outcome(50), outcome(0)));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> event.requireStartWithin(BOUNDS))
                .withMessageContaining(event.eventId().toString());
    }

    @Test
    @DisplayName("Given a printed weight below a positive floor, requireStartWithin throws")
    void requireStartWithin_weightBelowFloor_throws() {
        var event =
                new FutureEventDefinition(UUID.randomUUID(), "title", List.of(outcome(60), outcome(35), outcome(5)));
        var bounds = new ProbabilityBounds(10, 90);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> event.requireStartWithin(bounds));
    }

    // --- OutcomeDefinition constructor ---

    @Test
    @DisplayName("Given null outcomeId, OutcomeDefinition throws NullPointerException")
    void outcomeDefinition_nullOutcomeId_throws() {
        assertThatNullPointerException().isThrownBy(() -> new OutcomeDefinition(null, "description", 33));
    }

    @Test
    @DisplayName("Given null description, OutcomeDefinition throws NullPointerException")
    void outcomeDefinition_nullDescription_throws() {
        var id = UUID.randomUUID();
        assertThatNullPointerException().isThrownBy(() -> new OutcomeDefinition(id, null, 33));
    }

    @Test
    @DisplayName("Given negative probability, OutcomeDefinition throws IllegalArgumentException")
    void outcomeDefinition_negativeProbability_throws() {
        var id = UUID.randomUUID();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new OutcomeDefinition(id, "description", -1));
    }

    @Test
    @DisplayName("Given probability above 100, OutcomeDefinition throws IllegalArgumentException")
    void outcomeDefinition_probabilityAbove100_throws() {
        var id = UUID.randomUUID();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new OutcomeDefinition(id, "description", 101));
    }
}
