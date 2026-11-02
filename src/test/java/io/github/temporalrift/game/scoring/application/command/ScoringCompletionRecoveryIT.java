package io.github.temporalrift.game.scoring.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.scoring.domain.context.PendingEraScoringCompletion;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@GameServiceIntegrationTest
class ScoringCompletionRecoveryIT {

    @Autowired
    EraScoringContextRepository contextRepository;

    @Autowired
    TimelineOutcomeInboxRepository outcomeInboxRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ScoringCompletionSweep sweep;

    @Test
    void sweep_completesAnEraWhoseRecordingAndResolutionMissedEachOther() {
        // The state two overlapping transactions leave behind: the listener recorded the era's Revisionist action and
        // Activist declaration, the consumer stored the outcome and the resolution barrier, and each resolved before
        // the other's write was visible — so everything is stored and nothing is resolved.
        var gameId = UUID.randomUUID();
        var revisionistId = UUID.randomUUID();
        var activistId = UUID.randomUUID();
        var eraNumber = 1;
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(_ -> {
            contextRepository.upsertPlayerFaction(gameId, revisionistId, Faction.REVISIONISTS);
            contextRepository.upsertPlayerFaction(gameId, activistId, Faction.ACTIVISTS);
            contextRepository.upsertExpectedOutcomeCount(gameId, eraNumber, 1);
            contextRepository.upsertActivistDeclaration(new ActivistDeclarationRecorded(
                    gameId, eraNumber, 1, activistId, SpecialAction.RALLY, eventId, outcomeId));
            contextRepository.recordRevisionistAction(
                    gameId, eraNumber, revisionistId, SpecialAction.REWRITE, eventId, outcomeId);
            outcomeInboxRepository.save(new OutcomeApplied(gameId, eraNumber, eventId, outcomeId, List.of()));
            contextRepository.saveEraResolutionCompleted(new EraResolutionCompleted(
                    gameId,
                    eraNumber,
                    List.of(new EraResolutionCompleted.TerminalResolution(
                            eventId, 0, EraResolutionCompleted.TerminalState.OUTCOME_APPLIED, outcomeId))));
            contextRepository.markActionFactsReady(gameId, eraNumber);
        });
        assertThat(contextRepository.activistDeclarationsResolved(gameId, eraNumber))
                .isFalse();
        assertThat(contextRepository.revisionistActionsResolved(gameId, eraNumber))
                .isFalse();
        assertThat(contextRepository.findResolvedErasNotYetScored())
                .contains(new PendingEraScoringCompletion(gameId, eraNumber));

        sweep.sweep();

        assertThat(contextRepository.activistDeclarationsResolved(gameId, eraNumber))
                .isTrue();
        assertThat(contextRepository.revisionistActionsResolved(gameId, eraNumber))
                .isTrue();
        assertThat(contextRepository.findResolvedErasNotYetScored())
                .noneMatch(pending -> pending.gameId().equals(gameId));
    }
}
