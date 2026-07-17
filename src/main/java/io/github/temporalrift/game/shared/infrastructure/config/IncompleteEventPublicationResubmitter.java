package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically resubmits stale incomplete event publications instead of relying on
 * {@code republish-outstanding-events-on-restart}. Restart-bound republication is unsafe with more
 * than one instance — every restart replays *all* incomplete publications, including work that is
 * merely in flight on live instances — and it leaves events from a crashed instance undelivered
 * until something happens to restart.
 *
 * <p>The age threshold keeps in-flight publications out of the sweep; only publications that have
 * been incomplete far longer than any healthy listener takes are resubmitted. A permanently failing
 * listener is retried on every sweep — deliberately loud in the logs rather than silently parked.
 */
@Component
class IncompleteEventPublicationResubmitter {

    private static final Duration MIN_AGE = Duration.ofMinutes(1);

    private final IncompleteEventPublications incompletePublications;

    IncompleteEventPublicationResubmitter(IncompleteEventPublications incompletePublications) {
        this.incompletePublications = incompletePublications;
    }

    @Scheduled(fixedDelayString = "${game.timers.event-resubmit-ms:30000}")
    void resubmitStale() {
        incompletePublications.resubmitIncompletePublicationsOlderThan(MIN_AGE);
    }
}
