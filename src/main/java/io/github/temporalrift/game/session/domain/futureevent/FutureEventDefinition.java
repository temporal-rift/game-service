package io.github.temporalrift.game.session.domain.futureevent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FutureEventDefinition(UUID eventId, String title, List<OutcomeDefinition> outcomes) {

    public FutureEventDefinition {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(outcomes, "outcomes must not be null");

        if (outcomes.size() != 3) {
            throw new IllegalArgumentException("Exactly 3 outcomes must be defined");
        }
        if (outcomes.stream().mapToInt(OutcomeDefinition::probability).sum() != 100) {
            throw new IllegalArgumentException("Sum of outcome probabilities must equal 100");
        }
        outcomes = List.copyOf(outcomes);
    }

    public record OutcomeDefinition(UUID outcomeId, String description, int probability) {

        public OutcomeDefinition {
            Objects.requireNonNull(outcomeId, "outcomeId must not be null");
            Objects.requireNonNull(description, "description must not be null");

            if (probability < 0 || probability > 100) {
                throw new IllegalArgumentException("Probability must be between 0 and 100");
            }
        }
    }
}
