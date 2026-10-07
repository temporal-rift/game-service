package io.github.temporalrift.game.shared.domain.port.out;

import java.time.Instant;
import java.util.UUID;

/** A module's view of its pending work, aggregated into the service execution checkpoint. */
public interface ExecutionProbe {

    Observation observe(Instant now);

    /**
     * @param gameId the game this deployment hosts, or {@code null} when the module does not own one or none exists
     * @param gameEnded whether the hosted game reached its terminal state
     * @param dueTimers unhandled timers whose deadline is at or before {@code now}
     * @param continuations recorded facts whose saga continuation has not completed
     * @param nextDeadline earliest unhandled deadline after {@code now}, or {@code null}
     */
    record Observation(UUID gameId, boolean gameEnded, int dueTimers, int continuations, Instant nextDeadline) {}
}
