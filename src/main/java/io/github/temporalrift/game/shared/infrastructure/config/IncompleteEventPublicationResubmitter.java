package io.github.temporalrift.game.shared.infrastructure.config;

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
 * <p>The age threshold is a heuristic, not an in-flight guard: Modulith tracks completion, not
 * execution, so a listener still running past the threshold gets resubmitted concurrently. The
 * threshold is therefore a declared upper bound on listener runtime — keep it well above the
 * slowest listener, and keep listeners redelivery-tolerant (Modulith is at-least-once regardless).
 * A permanently failing listener is retried on every sweep — deliberately loud in the logs rather
 * than silently parked.
 */
@Component
class IncompleteEventPublicationResubmitter {

    private final IncompleteEventPublications incompletePublications;
    private final TimerProperties timerProperties;

    IncompleteEventPublicationResubmitter(
            IncompleteEventPublications incompletePublications, TimerProperties timerProperties) {
        this.incompletePublications = incompletePublications;
        this.timerProperties = timerProperties;
    }

    @Scheduled(fixedDelayString = "${game.timers.event-resubmit-interval}")
    void resubmitStale() {
        incompletePublications.resubmitIncompletePublicationsOlderThan(timerProperties.eventResubmitMinAge());
    }
}
