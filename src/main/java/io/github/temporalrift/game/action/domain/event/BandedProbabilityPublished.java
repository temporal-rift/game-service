package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.ProbabilityBand;

public record BandedProbabilityPublished(UUID gameId, int eraNumber, List<EventBandState> eventStates) {

    public record EventBandState(UUID eventId, List<OutcomeBandState> outcomes) {}

    public record OutcomeBandState(UUID outcomeId, ProbabilityBand band) {}
}
