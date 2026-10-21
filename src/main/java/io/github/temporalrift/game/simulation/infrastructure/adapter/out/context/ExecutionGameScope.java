package io.github.temporalrift.game.simulation.infrastructure.adapter.out.context;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.event.ExecutionClosed;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;
import io.github.temporalrift.game.shared.domain.port.out.GameScope;

/** Admits only the game the open context hosts; with no context open, no game. */
@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class ExecutionGameScope implements GameScope {

    private final PinnedExecutionContext pinned;
    private final List<ExecutionProbe> probes;
    private final Clock clock;
    private volatile UUID hosted;
    private volatile long closures;

    ExecutionGameScope(PinnedExecutionContext pinned, List<ExecutionProbe> probes, Clock clock) {
        this.pinned = pinned;
        this.probes = probes;
        this.clock = clock;
    }

    @Override
    public boolean admits(UUID gameId) {
        if (gameId == null || pinned.current().isEmpty()) {
            return false;
        }
        var game = hosted;
        if (game == null) {
            var seen = closures;
            game = probes.stream()
                    .map(probe -> probe.observe(clock.instant()).gameId())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (game != null) {
                host(game, seen);
            }
        }
        return gameId.equals(game);
    }

    @EventListener
    synchronized void onClosed(ExecutionClosed closed) {
        hosted = null;
        closures++;
    }

    private synchronized void host(UUID game, long seen) {
        if (closures == seen) {
            hosted = game;
        }
    }
}
