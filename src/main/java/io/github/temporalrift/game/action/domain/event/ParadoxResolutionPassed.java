package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

/**
 * Private fact that a player passed an open paradox-resolution phase: it consumes their single phase slot like a
 * {@link ParadoxResolutionCardPlayed}, so timeline-service can close the phase once every player has submitted or
 * passed, but carries no card and applies nothing at close.
 */
public record ParadoxResolutionPassed(UUID gameId, int eraNumber, UUID playerId) implements ActionEventPayload {}
