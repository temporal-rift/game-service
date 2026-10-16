package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.action.domain.handselection.HandSelectionStatus;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;

@Component
@Profile("in-memory")
class InMemoryActionExecutionProbe implements ExecutionProbe {

    private static final String OPEN_SELECTION = HandSelectionStatus.OPEN.name();
    private static final String OPEN_DECLARATION = DeclarationPhaseStatus.OPEN.name();
    private static final String WAITING = ActionRoundSagaStatus.WAITING.name();

    private final InMemoryHandSelectionJpaRepository handSelections;
    private final InMemoryDeclarationPhaseJpaRepository declarationPhases;
    private final InMemoryActionRoundSagaStateJpaRepository roundSagas;

    InMemoryActionExecutionProbe(
            InMemoryHandSelectionJpaRepository handSelections,
            InMemoryDeclarationPhaseJpaRepository declarationPhases,
            InMemoryActionRoundSagaStateJpaRepository roundSagas) {
        this.handSelections = handSelections;
        this.declarationPhases = declarationPhases;
        this.roundSagas = roundSagas;
    }

    @Override
    public Observation observe(Instant now) {
        List<Instant> deadlines = Stream.of(
                        handSelections.project(
                                selection -> OPEN_SELECTION.equals(selection.status),
                                selection -> selection.selectionExpiresAt),
                        declarationPhases.project(
                                phase -> OPEN_DECLARATION.equals(phase.getStatus()),
                                DeclarationPhaseJpaEntity::getExpiresAt),
                        roundSagas.project(
                                saga -> WAITING.equals(saga.getStatus()),
                                ActionRoundSagaStateJpaEntity::getTimerExpiresAt))
                .flatMap(List::stream)
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
