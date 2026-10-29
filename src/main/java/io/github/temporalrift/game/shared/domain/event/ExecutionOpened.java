package io.github.temporalrift.game.shared.domain.event;

import java.util.UUID;

/**
 * The execution context of {@code caseKey} is being opened, so the deployment is about to play that context's game. It
 * is announced once, inside the transaction that opens the context and before it commits, not again when the same
 * context is configured a second time.
 */
public record ExecutionOpened(UUID caseKey) {}
