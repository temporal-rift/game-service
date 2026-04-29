package io.github.temporalrift.game.session.domain.futureevent;

import static org.assertj.core.api.Assertions.assertThat;
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

        // when / then
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> event.outcomes().add(extraOutcome));
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
