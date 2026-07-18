package io.github.temporalrift.game.scoring.domain.event;

import java.util.List;
import java.util.UUID;

public record OutcomeApplied(
        UUID gameId, int eraNumber, UUID eventId, UUID winningOutcomeId, List<ProbabilityState> finalProbabilities) {

    public record ProbabilityState(UUID outcomeId, int probability) {}
}
