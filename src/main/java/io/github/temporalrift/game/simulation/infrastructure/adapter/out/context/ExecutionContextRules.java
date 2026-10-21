package io.github.temporalrift.game.simulation.infrastructure.adapter.out.context;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionRules;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContext;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class ExecutionContextRules implements ExecutionRules {

    private final PinnedExecutionContext pinned;
    private final AtomicReference<Served> served = new AtomicReference<>();

    ExecutionContextRules(PinnedExecutionContext pinned) {
        this.pinned = pinned;
    }

    @Override
    public Optional<Rules> current() {
        return pinned.current().map(this::rulesOf);
    }

    // Every rule lookup asks, so the flat form is built once per pinned context.
    private Rules rulesOf(ExecutionContext context) {
        var cached = served.get();
        if (cached != null && cached.context() == context) {
            return cached.rules();
        }
        var rules = new Rules(context.rulesDigest(), context.rules().bindable());
        served.set(new Served(context, rules));
        return rules;
    }

    private record Served(ExecutionContext context, Rules rules) {}
}
