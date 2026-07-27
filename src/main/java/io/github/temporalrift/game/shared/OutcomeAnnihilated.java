package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Cross-module event: the action module records an Eraser's Annihilate targeting an event's outcome;
 * the scoring module projects it into {@code EraScoringContext.eventOutcomes()} to shrink the event's
 * active outcome count. In-process only — unlike {@code SpecialActionPlayed} it never crosses to Kafka,
 * since no other service needs it. Lives in {@code game.shared} for the same reason as {@link EventsDrawn}.
 */
public record OutcomeAnnihilated(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId) {}
