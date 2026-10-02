package io.github.temporalrift.game.action.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Public lifecycle fact carrying a newly opened declaration window's persisted expiry; it names no participant. */
public record DeclarationWindowOpened(UUID gameId, int eraNumber, Instant expiresAt) implements ActionEventPayload {}
