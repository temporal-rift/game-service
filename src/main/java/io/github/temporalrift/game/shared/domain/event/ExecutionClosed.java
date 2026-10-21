package io.github.temporalrift.game.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The execution context of {@code caseKey} was closed at {@code closedAt} and its work discarded. A listener that
 * runs late must only drop what existed at {@code closedAt}, never what the next context created since.
 */
public record ExecutionClosed(UUID caseKey, Instant closedAt) {}
