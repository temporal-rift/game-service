package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

/**
 * One timeline-confirmed Annihilate by {@code playerId}: {@code erased} when the target was still eligible, and
 * {@code wasLeading} when it held the highest eligible probability (ties included), both before that round's
 * Annihilates applied.
 *
 * <p>Era scoring adds conditions this fact cannot decide alone: a writing may shield the erasure, and while
 * {@code annihilation-requires-consequence} is on its event's outcome must report that the erasure changed the winner.
 *
 * @param roundNumber the action round the Annihilate resolved in; null for a fact recorded before rounds were kept
 */
public record AnnihilationFact(
        UUID eventId, UUID outcomeId, UUID playerId, Integer roundNumber, boolean erased, boolean wasLeading) {

    /** Whether this Annihilate erased an outcome that led its event, required for {@code ANNIHILATED_OUTCOME}. */
    public boolean creditable() {
        return erased && wasLeading;
    }
}
