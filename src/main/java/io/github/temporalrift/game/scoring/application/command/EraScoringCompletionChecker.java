package io.github.temporalrift.game.scoring.application.command;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;

/**
 * Decides whether an era is ready to be scored and triggers {@link UpdateScoresCommandHandler} exactly
 * once. Two independent signals must both be true before scoring can run: every expected {@code
 * OutcomeApplied} has arrived, and the action module's Modulith-internal projection facts for the era
 * (e.g. {@code ForesightDeclared}, {@code EraActionFactsFinalized}) are durably recorded. Because
 * {@code @ApplicationModuleListener} dispatch is asynchronous, either signal can be the one that arrives
 * last — this class is called from both directions so whichever is last is the one that completes scoring.
 */
@Component
public class EraScoringCompletionChecker {

    private final EraScoringContextRepository contextRepository;
    private final TimelineOutcomeInboxRepository outcomeInboxRepository;
    private final ScoringEraCompletionRepository scoringEraCompletionRepository;
    private final UpdateScoresCommandHandler updateScoresCommandHandler;
    private final ApplicationEventPublisher applicationEventPublisher;

    public EraScoringCompletionChecker(
            EraScoringContextRepository contextRepository,
            TimelineOutcomeInboxRepository outcomeInboxRepository,
            ScoringEraCompletionRepository scoringEraCompletionRepository,
            UpdateScoresCommandHandler updateScoresCommandHandler,
            ApplicationEventPublisher applicationEventPublisher) {
        this.contextRepository = Objects.requireNonNull(contextRepository);
        this.outcomeInboxRepository = Objects.requireNonNull(outcomeInboxRepository);
        this.scoringEraCompletionRepository = Objects.requireNonNull(scoringEraCompletionRepository);
        this.updateScoresCommandHandler = Objects.requireNonNull(updateScoresCommandHandler);
        this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher);
    }

    // Callers that already run inside a transaction (Kafka listeners, @ApplicationModuleListener) simply
    // join it, unchanged from before this annotation existed. ScoringCompletionSweep's plain @Scheduled
    // method has no ambient transaction, and tryMarkScoringComplete below requires one (MANDATORY) --
    // without this, every sweep invocation that reaches real work throws and is silently swallowed by
    // the sweep's own per-item catch, so scoring is never actually completed by the sweep.
    @Transactional
    public void tryComplete(UUID gameId, int eraNumber) {
        if (!contextRepository.actionFactsReady(gameId, eraNumber)) {
            return;
        }
        if (!contextRepository.eraResolutionCompleted(gameId, eraNumber)) {
            return;
        }
        resolveOutstanding(gameId, eraNumber);
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

    // The projection listener that records the era's Revisionist actions and Activist declarations and the Kafka
    // consumer that stores its outcomes each resolve right after their own write, inside their own transaction. When
    // the two overlap, each can miss the other's uncommitted write and nothing resolves the rows afterwards, so the
    // era would never be scored. Resolving again here, where every trigger (including the sweep) decides readiness,
    // repairs that. Both resolutions are idempotent and return or score only what they resolve in this call.
    private void resolveOutstanding(UUID gameId, int eraNumber) {
        contextRepository.resolveRevisionistActions(gameId, eraNumber);
        contextRepository
                .resolveActivistDeclarations(gameId, eraNumber)
                .forEach(applicationEventPublisher::publishEvent);
    }
}
