package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

@Embeddable
record FutureEventOutcomeValue(
        @Column(name = "outcome_id", nullable = false) UUID outcomeId,

        @Column(name = "initial_probability", nullable = false)
        int initialProbability) {

    static FutureEventOutcomeValue fromDomain(FutureEventDefinitionPort.OutcomeDefinition outcome) {
        return new FutureEventOutcomeValue(outcome.outcomeId(), outcome.initialProbability());
    }

    FutureEventDefinitionPort.OutcomeDefinition toDomain() {
        return new FutureEventDefinitionPort.OutcomeDefinition(outcomeId, initialProbability);
    }
}
