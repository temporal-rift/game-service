package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

/**
 * One timeline-confirmed Annihilate by {@code playerId}: {@code erased} when the target was still eligible, and
 * {@code wasLeading} when it held the highest eligible probability (ties included), both before that round's
 * Annihilates applied.
 *
 * @param roundNumber the action round the Annihilate resolved in; null for a fact recorded before rounds were kept
 */
public record AnnihilationFact(
        UUID eventId, UUID outcomeId, UUID playerId, Integer roundNumber, boolean erased, boolean wasLeading) {

    /** Only erasing an outcome that led its event earns {@code ANNIHILATED_OUTCOME}, unless a writing shields it. */
    public boolean creditable() {
        return erased && wasLeading;
    }
}
