package io.github.temporalrift.game.simulation.domain.execution;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record ClockAdvance(UUID operationId, long expectedRevision, Instant targetTime) {

    public ClockAdvance {
        targetTime = Objects.requireNonNull(targetTime, "targetTime").truncatedTo(ChronoUnit.MICROS);
        if (expectedRevision < 0) {
            throw new InvalidClockAdvanceException("expectedRevision must not be negative");
        }
    }
}
