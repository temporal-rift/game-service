package io.github.temporalrift.game.scoring.application.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.context.PendingEraScoringCompletion;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;

/**
 * Database-driven safety net for era scoring completion. {@link EraScoringCompletionChecker} is
 * triggered from several independent, asynchronously-dispatched signals (Kafka timeline.events
 * consumption and local Modulith listeners); each only rechecks completion once, right after its own
 * write. If two triggers interleave so that neither's read sees the other's just-committed write in
 * time, completion is never reattempted and the era wedges — this sweep, which any instance can run
 * against the shared tables, guarantees a resolved-but-unscored era is always eventually retried.
 *
 * <p>Every instance sweeps concurrently without coordination: {@code tryMarkScoringComplete}'s
 * {@code INSERT ... ON CONFLICT DO NOTHING} makes duplicate completion a no-op.
 */
@Component
class ScoringCompletionSweep {

    private static final Logger log = LoggerFactory.getLogger(ScoringCompletionSweep.class);

    private final EraScoringContextRepository contextRepository;
    private final EraScoringCompletionChecker completionChecker;

    ScoringCompletionSweep(
            EraScoringContextRepository contextRepository, EraScoringCompletionChecker completionChecker) {
        this.contextRepository = contextRepository;
        this.completionChecker = completionChecker;
    }

    @Scheduled(fixedDelayString = "${game.timers.scoring-completion-sweep-interval}")
    void sweep() {
        contextRepository.findResolvedErasNotYetScored().forEach(this::process);
    }

    private void process(PendingEraScoringCompletion pending) {
        // One failing era must not starve the rest of the sweep batch.
        try {
            completionChecker.tryComplete(pending.gameId(), pending.eraNumber());
        } catch (RuntimeException ex) {
            log.error("Scoring completion sweep failed for game {} era {}", pending.gameId(), pending.eraNumber(), ex);
        }
    }
}
