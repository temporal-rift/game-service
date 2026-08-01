package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

/**
 * Scoring-owned record of faction disclosures that occurred before the final game-end reveal.
 *
 * <p>The final {@code FactionRevealed} event must not be recorded through this port before the
 * Revisionist end-game eligibility check, because that reveal is the signal which evaluates the
 * {@code FACTION_UNIDENTIFIED} rule.
 */
public interface FactionIdentificationRepository {

    boolean wasIdentifiedBeforeGameEnd(UUID gameId, UUID playerId);

    void recordIdentification(UUID gameId, UUID playerId);
}
