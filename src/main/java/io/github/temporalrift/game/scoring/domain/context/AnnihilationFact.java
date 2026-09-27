package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

/**
 * One timeline-confirmed Annihilate by {@code playerId}: {@code erased} when the target was still eligible, and
 * {@code wasLeading} when it held the highest eligible probability (ties included), both before that round's
 * Annihilates applied.
 */
public record AnnihilationFact(UUID eventId, UUID outcomeId, UUID playerId, boolean erased, boolean wasLeading) {

    /** Only erasing an outcome that led its event earns {@code ANNIHILATED_OUTCOME}. */
    public boolean creditable() {
        return erased && wasLeading;
    }
}
