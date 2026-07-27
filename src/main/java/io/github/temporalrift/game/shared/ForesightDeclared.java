package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Cross-module event: the action module records a Prophet's Foresight declaration of an event's
 * "written" outcome; the scoring module projects it into {@code EraScoringContext.eventOutcomes()}.
 * In-process only — unlike {@code SpecialActionPlayed} it never crosses to Kafka, since no other
 * service needs it. Lives in {@code game.shared} for the same reason as {@link EventsDrawn}.
 */
public record ForesightDeclared(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId) {}
