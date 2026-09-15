package io.github.temporalrift.game.session.application.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.shared.domain.event.ScoresUpdated;

/**
 * Database-driven safety net for the era saga's {@code WAITING_SCORES} transition. {@code
 * ActionRoundClosed} (round 3) sets the saga to {@code WAITING_SCORES} and {@code ScoresUpdated} fires
 * from an independent, asynchronously-dispatched chain (timeline-service resolution -> scoring); with no
 * ordering guarantee between them, {@code ScoresUpdated} can arrive before the status write commits, and
 * {@link EraSagaAdvancer#handleScoresUpdated} silently no-ops. This sweep, which any instance can run
 * against the shared tables, guarantees a scored-but-not-advanced era is always eventually retried.
 *
 * <p>Every instance sweeps concurrently without coordination: {@code handleScoresUpdated}'s own {@code
 * WAITING_SCORES} filter makes a duplicate transition attempt a no-op. Only an item that {@code
 * handleScoresUpdated} reports as actually advanced increments {@link EraSagaRecoveryMetrics} — a no-op
 * caused by a concurrent sweep pass or listener redelivery already having claimed the same era does not.
 */
@Component
class EraSagaScoresUpdatedSweep {

    private static final Logger log = LoggerFactory.getLogger(EraSagaScoresUpdatedSweep.class);

    private final EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox;
    private final EraSagaAdvancer eraSagaAdvancer;
    private final EraSagaRecoveryMetrics recoveryMetrics;

    EraSagaScoresUpdatedSweep(
            EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox,
            EraSagaAdvancer eraSagaAdvancer,
            EraSagaRecoveryMetrics recoveryMetrics) {
        this.scoresUpdatedInbox = scoresUpdatedInbox;
        this.eraSagaAdvancer = eraSagaAdvancer;
        this.recoveryMetrics = recoveryMetrics;
    }

    @Scheduled(fixedDelayString = "${game.timers.era-saga-scores-updated-sweep-interval}")
    void sweep() {
        scoresUpdatedInbox.findRecordedButNotAdvanced().forEach(this::process);
    }

    private void process(ScoresUpdated pending) {
        // One failing era must not starve the rest of the sweep batch.
        try {
            if (eraSagaAdvancer.handleScoresUpdated(pending.gameId(), pending)) {
                recoveryMetrics.recordScoresUpdatedRecovery();
            }
        } catch (RuntimeException ex) {
            log.error(
                    "Era saga scores-updated sweep failed for game {} era {}",
                    pending.gameId(),
                    pending.eraNumber(),
                    ex);
        }
    }
}
