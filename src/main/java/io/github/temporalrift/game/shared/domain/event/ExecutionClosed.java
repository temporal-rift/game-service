package io.github.temporalrift.game.shared.domain.event;

import java.util.UUID;

/** The execution context of {@code caseKey} was closed and its work discarded. */
public record ExecutionClosed(UUID caseKey) {}
