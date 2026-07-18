package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record ParadoxCascaded(
        UUID gameId,
        int eraNumber,
        UUID paradoxId,
        UUID affectedEventId,
        List<ProbabilityState> carryForwardProbabilityState) {

    public record ProbabilityState(UUID outcomeId, int probability) {}
}
