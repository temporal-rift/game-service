package io.github.temporalrift.game.simulation.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.simulation.domain.execution.Execution;

public interface ExecutionRepository {

    Optional<Execution> find();

    /** Reads the execution holding a write lock until the surrounding transaction ends. */
    Optional<Execution> findWithLock();

    /** Returns whether this call stored the execution; {@code false} means one was already configured. */
    boolean insertIfAbsent(Execution execution);

    void update(Execution execution);

    /** Removes the execution and its seats, and remembers its case as the most recently closed. */
    void close(UUID caseKey);

    /** The case of the context closed last, which stays known while later contexts come and go. */
    Optional<UUID> lastClosedCaseKey();
}
