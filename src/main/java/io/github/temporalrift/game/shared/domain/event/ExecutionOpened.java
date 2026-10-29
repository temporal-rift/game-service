package io.github.temporalrift.game.shared.domain.event;

import java.util.UUID;

/**
 * The execution context of {@code caseKey} was opened, so the deployment now plays that context's game. It is announced
 * once, when the context is opened, not again when the same context is configured a second time.
 */
public record ExecutionOpened(UUID caseKey) {}
