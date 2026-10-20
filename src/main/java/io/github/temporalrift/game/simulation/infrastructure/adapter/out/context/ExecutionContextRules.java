package io.github.temporalrift.game.simulation.infrastructure.adapter.out.context;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionRules;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class ExecutionContextRules implements ExecutionRules {

    private final PinnedExecutionContext pinned;

    ExecutionContextRules(PinnedExecutionContext pinned) {
        this.pinned = pinned;
    }

    @Override
    public Optional<Rules> current() {
        return pinned.current()
                .map(context -> new Rules(context.rulesDigest(), context.rules().bindable()));
    }
}
