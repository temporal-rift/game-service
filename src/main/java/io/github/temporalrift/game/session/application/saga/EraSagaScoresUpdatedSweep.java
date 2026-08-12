package io.github.temporalrift.game.session.application.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.shared.ScoresUpdated;

/**
 * Database-driven safety net for the era saga's {@code WAITING_SCORES} transition. {@code
 * ActionRoundClosed} (round 3) sets the saga to {@code WAITING_SCORES} and {@code ScoresUpdated} fires
 * from an independent, asynchronously-dispatched chain (timeline-service resolution -> scoring); with no
 * ordering guarantee between them, {@code ScoresUpdated} can arrive before the status write commits, and
 * {@link EraSagaAdvancer#handleScoresUpdated} silently no-ops. This sweep, which any instance can run
 * against the shared tables, guarantees a scored-but-not-advanced era is always eventually retried.
 *
 * <p>Every instance sweeps concurrently without coordination: {@code handleScoresUpdated}'s own {@code
 * WAITING_SCORES} filter makes a duplicate transition attempt a no-op.
 */
@Component
class EraSagaScoresUpdatedSweep {

    private static final Logger log = LoggerFactory.getLogger(EraSagaScoresUpdatedSweep.class);

    private final EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox;
    private final EraSagaAdvancer eraSagaAdvancer;

    EraSagaScoresUpdatedSweep(EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox, EraSagaAdvancer eraSagaAdvancer) {
        this.scoresUpdatedInbox = scoresUpdatedInbox;
        this.eraSagaAdvancer = eraSagaAdvancer;
    }

    @Scheduled(fixedDelayString = "${game.timers.era-saga-scores-updated-sweep-interval}")
    void sweep() {
        scoresUpdatedInbox.findRecordedButNotAdvanced().forEach(this::process);
    }

    private void process(ScoresUpdated pending) {
        // One failing era must not starve the rest of the sweep batch.
        try {
            eraSagaAdvancer.handleScoresUpdated(pending.gameId(), pending);
        } catch (RuntimeException ex) {
            log.error(
                    "Era saga scores-updated sweep failed for game {} era {}",
                    pending.gameId(),
                    pending.eraNumber(),
                    ex);
        }
    }
}
