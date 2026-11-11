package io.github.temporalrift.game.scoring.domain.context;

import java.util.Objects;
import java.util.UUID;

/**
 * Scoring-relevant outcome facts for one event in a resolved era.
 *
 * @param writtenOutcomeId the outcome id that was written by the Prophet player, null if no Prophet wrote this event
 * @param writtenRoundNumber the action round of that writing, null if nothing was written or the round is unknown
 */
public record EventOutcomeFact(UUID eventId, UUID winningOutcomeId, UUID writtenOutcomeId, Integer writtenRoundNumber) {

    /**
     * Fate protects what is written: {@code annihilation} is a shielded erasure when it erased this event's written
     * outcome in the writing's round or a later one. Leading or not does not matter. An erasure whose round, or a
     * writing whose round, is unknown is never shielded.
     */
    public boolean shields(AnnihilationFact annihilation) {
        Objects.requireNonNull(annihilation, "annihilation must not be null");
        return annihilation.erased()
                && writtenOutcomeId != null
                && writtenRoundNumber != null
                && annihilation.roundNumber() != null
                && eventId.equals(annihilation.eventId())
                && writtenOutcomeId.equals(annihilation.outcomeId())
                && annihilation.roundNumber() >= writtenRoundNumber;
    }
}
