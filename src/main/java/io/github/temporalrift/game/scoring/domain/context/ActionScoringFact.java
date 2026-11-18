package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.domain.model.Faction;

/**
 * An explicit scoring fact produced by a player action during the era.
 * Used for Activist, Revisionist, and Eraser rules that require explicit card plays.
 *
 * @param targetEventId the event the bet behind this fact named; null when no bet stands behind it
 * @param targetOutcomeId the outcome the bet behind this fact named; null when no bet stands behind it
 */
public record ActionScoringFact(
        UUID playerId, Faction faction, ScoreReason reason, UUID targetEventId, UUID targetOutcomeId) {

    /** A fact with no bet behind it. */
    public ActionScoringFact(UUID playerId, Faction faction, ScoreReason reason) {
        this(playerId, faction, reason, null, null);
    }

    /** Whether this fact's bet named the outcome the given Annihilate erased. */
    public boolean betOn(AnnihilationFact annihilation) {
        return annihilation.erased()
                && annihilation.eventId().equals(targetEventId)
                && annihilation.outcomeId().equals(targetOutcomeId);
    }
}
