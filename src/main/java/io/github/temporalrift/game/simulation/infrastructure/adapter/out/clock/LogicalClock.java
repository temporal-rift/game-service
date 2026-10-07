package io.github.temporalrift.game.simulation.infrastructure.adapter.out.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.LogicalClockControl;

/** The only time source of an isolated deployment: it moves solely when the control interface advances it. */
@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class LogicalClock extends Clock implements LogicalClockControl {

    private final ExecutionRepository executions;
    private volatile Instant current;

    LogicalClock(ExecutionRepository executions) {
        this.executions = executions;
    }

    @Override
    public Instant instant() {
        var now = current;
        if (now == null) {
            // Before configuration no gameplay exists, so nothing may look due.
            now = executions.find().map(Execution::logicalTime).orElse(null);
            if (now == null) {
                return Instant.EPOCH;
            }
            advanceTo(now);
        }
        return current;
    }

    @Override
    public synchronized void advanceTo(Instant instant) {
        if (current == null || instant.isAfter(current)) {
            current = instant;
        }
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
