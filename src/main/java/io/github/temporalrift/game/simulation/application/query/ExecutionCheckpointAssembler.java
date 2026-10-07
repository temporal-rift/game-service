package io.github.temporalrift.game.simulation.application.query;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionCheckpoint;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionState;
import io.github.temporalrift.game.simulation.domain.port.out.OutboxProbe;
import io.github.temporalrift.game.simulation.domain.port.out.SourceWatermarks;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
public class ExecutionCheckpointAssembler {

    private final List<ExecutionProbe> probes;
    private final OutboxProbe outbox;
    private final SourceWatermarks watermarks;

    ExecutionCheckpointAssembler(List<ExecutionProbe> probes, OutboxProbe outbox, SourceWatermarks watermarks) {
        this.probes = probes;
        this.outbox = outbox;
        this.watermarks = watermarks;
    }

    public ExecutionCheckpoint assemble(Execution execution) {
        var observations = probes.stream()
                .map(probe -> probe.observe(execution.logicalTime()))
                .toList();
        var gameId = observations.stream()
                .map(ExecutionProbe.Observation::gameId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        var ended = observations.stream().anyMatch(ExecutionProbe.Observation::gameEnded);
        var dueTimers = observations.stream()
                .mapToInt(ExecutionProbe.Observation::dueTimers)
                .sum();
        var continuations = observations.stream()
                .mapToInt(ExecutionProbe.Observation::continuations)
                .sum();
        var nextDeadline = observations.stream()
                .map(ExecutionProbe.Observation::nextDeadline)
                .filter(Objects::nonNull)
                .min(Comparator.<Instant>naturalOrder())
                .orElse(null);
        var outboxPending = outbox.pendingPublications();
        return new ExecutionCheckpoint(
                execution.context().caseKey(),
                execution.context().manifestDigest(),
                execution.revision(),
                execution.logicalTime(),
                gameId,
                stateOf(gameId, ended),
                outboxPending == 0 && continuations == 0 && dueTimers == 0,
                outboxPending,
                continuations,
                dueTimers,
                nextDeadline,
                watermarks.current());
    }

    private static ExecutionState stateOf(UUID gameId, boolean ended) {
        if (gameId == null) {
            return ExecutionState.READY;
        }
        return ended ? ExecutionState.TERMINAL : ExecutionState.ACTIVE;
    }
}
