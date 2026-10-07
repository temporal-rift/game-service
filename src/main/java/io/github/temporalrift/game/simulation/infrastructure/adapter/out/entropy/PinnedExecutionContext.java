package io.github.temporalrift.game.simulation.infrastructure.adapter.out.entropy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContext;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;

/** The configured context never changes once accepted, so it is read once and then served from memory. */
@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class PinnedExecutionContext {

    private final ExecutionRepository executions;
    private volatile ExecutionContext context;

    PinnedExecutionContext(ExecutionRepository executions) {
        this.executions = executions;
    }

    ExecutionContext require() {
        var pinned = context;
        if (pinned == null) {
            pinned = executions.find().map(Execution::context).orElseThrow(ExecutionNotConfiguredException::new);
            context = pinned;
        }
        return pinned;
    }
}
