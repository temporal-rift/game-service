package io.github.temporalrift.game.shared.domain.event;

import java.util.UUID;

/**
 * Cross-module event: the action module records a Prophet's Foresight declaration of an event's
 * "written" outcome; the scoring module projects it into {@code EraScoringContext.eventOutcomes()}.
 * In-process only — unlike {@code SpecialActionPlayed} it never crosses to Kafka, since no other
 * service needs it. Lives in {@code game.shared} for the same reason as {@link EventsDrawn}.
 *
 * @param roundNumber the action round the Foresight was played in — the event's write round; at least 1, so a
 *     publication stored without it is refused rather than read as an earlier round than any Annihilate
 */
public record ForesightDeclared(
        UUID gameId, int eraNumber, int roundNumber, UUID eventId, UUID outcomeId, UUID playerId) {

    public ForesightDeclared {
        EraActionFactsFinalized.requireRound(roundNumber);
    }
}
