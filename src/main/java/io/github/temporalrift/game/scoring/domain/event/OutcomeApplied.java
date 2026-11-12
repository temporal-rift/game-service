package io.github.temporalrift.game.scoring.domain.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One event's terminal outcome as published by timeline.
 *
 * @param consequentialErasures the erased outcome ids of this event whose erasure changed the winner; empty when none
 *     did, and null when the producer predates the field, so the earlier annihilation rule still applies
 */
public record OutcomeApplied(
        UUID gameId,
        int eraNumber,
        UUID eventId,
        UUID winningOutcomeId,
        List<ProbabilityState> finalProbabilities,
        List<UUID> consequentialErasures) {

    public OutcomeApplied {
        consequentialErasures = consequentialErasures == null ? null : List.copyOf(consequentialErasures);
    }

    /**
     * Whether this outcome credits the erasure of {@code erasedOutcomeId}: it does when the erasure changed the winner,
     * and also when the producer predates the list, so an older timeline keeps the earlier rule.
     */
    public boolean creditsErasureOf(UUID erasedOutcomeId) {
        Objects.requireNonNull(erasedOutcomeId, "erasedOutcomeId must not be null");
        return consequentialErasures == null || consequentialErasures.contains(erasedOutcomeId);
    }

    public record ProbabilityState(UUID outcomeId, int probability) {}
}
