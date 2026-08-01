package io.github.temporalrift.game.scoring.application.command;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;

/**
 * Decides whether an era is ready to be scored and triggers {@link UpdateScoresCommandHandler} exactly
 * once. Two independent signals must both be true before scoring can run: every expected {@code
 * OutcomeApplied} has arrived, and the action module's Modulith-internal projection facts for the era
 * (e.g. {@code ForesightDeclared}, {@code OutcomeAnnihilated}) are durably recorded. Because
 * {@code @ApplicationModuleListener} dispatch is asynchronous, either signal can be the one that arrives
 * last — this class is called from both directions so whichever is last is the one that completes scoring.
 */
@Component
public class EraScoringCompletionChecker {

    private final EraScoringContextRepository contextRepository;
    private final TimelineOutcomeInboxRepository outcomeInboxRepository;
    private final ScoringEraCompletionRepository scoringEraCompletionRepository;
    private final UpdateScoresCommandHandler updateScoresCommandHandler;

    public EraScoringCompletionChecker(
            EraScoringContextRepository contextRepository,
            TimelineOutcomeInboxRepository outcomeInboxRepository,
            ScoringEraCompletionRepository scoringEraCompletionRepository,
            UpdateScoresCommandHandler updateScoresCommandHandler) {
        this.contextRepository = Objects.requireNonNull(contextRepository);
        this.outcomeInboxRepository = Objects.requireNonNull(outcomeInboxRepository);
        this.scoringEraCompletionRepository = Objects.requireNonNull(scoringEraCompletionRepository);
        this.updateScoresCommandHandler = Objects.requireNonNull(updateScoresCommandHandler);
    }

    public void tryComplete(UUID gameId, int eraNumber) {
        if (!contextRepository.actionFactsReady(gameId, eraNumber)) {
            return;
        }
        if (!contextRepository.eraResolutionCompleted(gameId, eraNumber)) {
            return;
        }
        if (!contextRepository.activistDeclarationsResolved(gameId, eraNumber)) {
            return;
        }
        if (!contextRepository.revisionistActionsResolved(gameId, eraNumber)) {
            return;
        }
        var expectedCount = contextRepository.requiredAppliedOutcomeCount(gameId, eraNumber);
        var outcomes = outcomeInboxRepository.findByGameIdAndEraNumber(gameId, eraNumber);
        if (outcomes.size() < expectedCount) {
            return;
        }
        if (!scoringEraCompletionRepository.tryMarkScoringComplete(gameId, eraNumber)) {
            return;
        }
        updateScoresCommandHandler.handle(new UpdateEraScoresCommand(gameId, eraNumber, outcomes));
    }
}
