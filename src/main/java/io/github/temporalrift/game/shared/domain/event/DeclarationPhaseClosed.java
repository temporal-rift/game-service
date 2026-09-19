package io.github.temporalrift.game.shared.domain.event;

import java.util.UUID;

/**
 * Cross-module event: the action module closed a declaration window (on timer expiry) and the session
 * module's era saga may now advance to Action Round 1. Lives in {@code game.shared} - the neutral shared
 * kernel - so referencing it never creates a Spring Modulith module cycle.
 */
public record DeclarationPhaseClosed(UUID gameId, int eraNumber) {}
