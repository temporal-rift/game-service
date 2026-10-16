package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;

@Component
@Profile("in-memory")
class InMemoryActionExecutionProbe implements ExecutionProbe {

    private static final String OPEN = "OPEN";
    private static final String WAITING = "WAITING";

    private final HandSelectionJpaRepository handSelections;
    private final DeclarationPhaseJpaRepository declarationPhases;
    private final ActionRoundSagaStateJpaRepository roundSagas;

    InMemoryActionExecutionProbe(
            HandSelectionJpaRepository handSelections,
            DeclarationPhaseJpaRepository declarationPhases,
            ActionRoundSagaStateJpaRepository roundSagas) {
        this.handSelections = handSelections;
        this.declarationPhases = declarationPhases;
        this.roundSagas = roundSagas;
    }

    @Override
    public Observation observe(Instant now) {
        var deadlines = Stream.of(
                        handSelections.findAll().stream()
                                .filter(selection -> OPEN.equals(selection.status))
                                .map(selection -> selection.selectionExpiresAt),
                        declarationPhases.findAll().stream()
                                .filter(phase -> OPEN.equals(phase.getStatus()))
                                .map(DeclarationPhaseJpaEntity::getExpiresAt),
                        roundSagas.findAll().stream()
                                .filter(saga -> WAITING.equals(saga.getStatus()))
                                .map(ActionRoundSagaStateJpaEntity::getTimerExpiresAt))
                .flatMap(Function.identity())
                .toList();
        var due = (int)
                deadlines.stream().filter(deadline -> !deadline.isAfter(now)).count();
        var next = deadlines.stream()
                .filter(deadline -> deadline.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElse(null);
        return new Observation(null, false, due, 0, next);
    }
}
