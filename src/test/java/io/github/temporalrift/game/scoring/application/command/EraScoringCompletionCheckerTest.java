package io.github.temporalrift.game.scoring.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.domain.event.ActivistDeclarationResolved;

@ExtendWith(MockitoExtension.class)
class EraScoringCompletionCheckerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA_NUMBER = 1;

    @Mock
    EraScoringContextRepository contextRepository;

    @Mock
    TimelineOutcomeInboxRepository outcomeInboxRepository;

    @Mock
    ScoringEraCompletionRepository scoringEraCompletionRepository;

    @Mock
    UpdateScoresCommandHandler updateScoresCommandHandler;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    EraScoringCompletionChecker checker;

    @Test
    @DisplayName("action facts not ready — scoring not attempted at all")
    void tryComplete_actionFactsNotReady_noScoring() {
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(false);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        then(contextRepository).should(never()).expectedOutcomeCount(any(), anyInt());
        then(scoringEraCompletionRepository).should(never()).tryMarkScoringComplete(any(), anyInt());
        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    @Test
    @DisplayName("terminal-resolution barrier missing — scoring waits even when action facts are ready")
    void tryComplete_barrierMissing_noScoring() {
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(false);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        then(contextRepository).should(never()).resolveRevisionistActions(any(), anyInt());
        then(contextRepository).should(never()).resolveActivistDeclarations(any(), anyInt());
        then(contextRepository).should(never()).activistDeclarationsResolved(any(), anyInt());
        then(scoringEraCompletionRepository).should(never()).tryMarkScoringComplete(any(), anyInt());
    }

    @Test
    @DisplayName("action facts ready but outcomes below expected count — scoring not triggered")
    void tryComplete_belowExpectedCount_noScoring() {
        var outcome = outcomeApplied();
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(true);
        given(contextRepository.revisionistActionsResolved(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.requiredAppliedOutcomeCount(GAME_ID, ERA_NUMBER))
                .willReturn(3);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcome));

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        then(scoringEraCompletionRepository).should(never()).tryMarkScoringComplete(any(), anyInt());
        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    @Test
    @DisplayName("unresolved Activist declaration — scoring waits even when outcomes are complete")
    void tryComplete_unresolvedActivistDeclaration_noScoring() {
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(false);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        then(contextRepository).should(never()).expectedOutcomeCount(any(), anyInt());
        then(scoringEraCompletionRepository).should(never()).tryMarkScoringComplete(any(), anyInt());
    }

    @Test
    @DisplayName("unresolved Revisionist action — scoring waits even when Activist declarations are resolved")
    void tryComplete_unresolvedRevisionistAction_noScoring() {
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(true);
        given(contextRepository.revisionistActionsResolved(GAME_ID, ERA_NUMBER)).willReturn(false);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        then(contextRepository).should(never()).requiredAppliedOutcomeCount(any(), anyInt());
        then(scoringEraCompletionRepository).should(never()).tryMarkScoringComplete(any(), anyInt());
    }

    @Test
    @DisplayName("action facts ready and outcomes complete — claims era and triggers scoring")
    void tryComplete_readyAndComplete_triggersScoring() {
        var outcome = outcomeApplied();
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(true);
        given(contextRepository.revisionistActionsResolved(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.requiredAppliedOutcomeCount(GAME_ID, ERA_NUMBER))
                .willReturn(1);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcome));
        given(scoringEraCompletionRepository.tryMarkScoringComplete(GAME_ID, ERA_NUMBER))
                .willReturn(true);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        var captor = ArgumentCaptor.forClass(UpdateEraScoresCommand.class);
        then(updateScoresCommandHandler).should().handle(captor.capture());
        var command = captor.getValue();
        assertThat(command.gameId()).isEqualTo(GAME_ID);
        assertThat(command.eraNumber()).isEqualTo(ERA_NUMBER);
        assertThat(command.outcomes()).containsExactly(outcome);
    }

    @Test
    @DisplayName("era already claimed by another caller — handler not called")
    void tryComplete_alreadyClaimed_handlerNotCalled() {
        var outcome = outcomeApplied();
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(true);
        given(contextRepository.revisionistActionsResolved(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.requiredAppliedOutcomeCount(GAME_ID, ERA_NUMBER))
                .willReturn(1);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcome));
        given(scoringEraCompletionRepository.tryMarkScoringComplete(GAME_ID, ERA_NUMBER))
                .willReturn(false);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    @Test
    @DisplayName("overlapping resolutions missed the rows — the check resolves them first, then scoring completes")
    void tryComplete_unresolvedRowsLeftByAnOverlap_resolvesThemAndTriggersScoring() {
        var outcome = outcomeApplied();
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.resolveActivistDeclarations(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(declarationResolved()));
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(true);
        given(contextRepository.revisionistActionsResolved(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.requiredAppliedOutcomeCount(GAME_ID, ERA_NUMBER))
                .willReturn(1);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcome));
        given(scoringEraCompletionRepository.tryMarkScoringComplete(GAME_ID, ERA_NUMBER))
                .willReturn(true);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        var order = inOrder(contextRepository, scoringEraCompletionRepository);
        order.verify(contextRepository).resolveRevisionistActions(GAME_ID, ERA_NUMBER);
        order.verify(contextRepository).resolveActivistDeclarations(GAME_ID, ERA_NUMBER);
        order.verify(contextRepository).activistDeclarationsResolved(GAME_ID, ERA_NUMBER);
        order.verify(contextRepository).revisionistActionsResolved(GAME_ID, ERA_NUMBER);
        order.verify(scoringEraCompletionRepository).tryMarkScoringComplete(GAME_ID, ERA_NUMBER);
        then(updateScoresCommandHandler).should().handle(any());
    }

    @Test
    @DisplayName("a declaration resolved by the check is announced exactly once, however often the check repeats")
    void tryComplete_declarationResolvedByTheCheck_publishedOnce() {
        var resolved = declarationResolved();
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.resolveActivistDeclarations(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(resolved), List.of());
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(false);

        checker.tryComplete(GAME_ID, ERA_NUMBER);
        checker.tryComplete(GAME_ID, ERA_NUMBER);

        var captor = ArgumentCaptor.forClass(Object.class);
        then(applicationEventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getAllValues()).containsExactly(resolved);
    }

    @Test
    @DisplayName("everything already resolved — the repeated check publishes no event and scores nothing twice")
    void tryComplete_alreadyResolved_publishesNothing() {
        given(contextRepository.actionFactsReady(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.eraResolutionCompleted(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.activistDeclarationsResolved(GAME_ID, ERA_NUMBER))
                .willReturn(true);
        given(contextRepository.revisionistActionsResolved(GAME_ID, ERA_NUMBER)).willReturn(true);
        given(contextRepository.requiredAppliedOutcomeCount(GAME_ID, ERA_NUMBER))
                .willReturn(1);
        given(outcomeInboxRepository.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                .willReturn(List.of(outcomeApplied()));
        given(scoringEraCompletionRepository.tryMarkScoringComplete(GAME_ID, ERA_NUMBER))
                .willReturn(false);

        checker.tryComplete(GAME_ID, ERA_NUMBER);

        then(applicationEventPublisher).shouldHaveNoInteractions();
        then(updateScoresCommandHandler).should(never()).handle(any());
    }

    private static ActivistDeclarationResolved declarationResolved() {
        return new ActivistDeclarationResolved(GAME_ID, ERA_NUMBER, UUID.randomUUID(), true);
    }

    private static OutcomeApplied outcomeApplied() {
        return new OutcomeApplied(GAME_ID, ERA_NUMBER, UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of());
    }
}
