package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextAnnihilatedOutcomeJpaRepository
        extends JpaRepository<ScoringContextAnnihilatedOutcomeJpaEntity, UUID> {

    List<ScoringContextAnnihilatedOutcomeJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber);

    // Idempotent on (game_id, era_number, event_id, outcome_id): redelivery of the same
    // OutcomeAnnihilated event, or a redundant Annihilate replayed against an already-annihilated
    // outcome, must never double-count toward endingOutcomeCount.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_annihilated_outcome
                        (id, game_id, era_number, event_id, outcome_id, player_id)
                    VALUES (:id, :gameId, :eraNumber, :eventId, :outcomeId, :playerId)
                    ON CONFLICT (game_id, era_number, event_id, outcome_id) DO NOTHING
                    """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("eventId") UUID eventId,
            @Param("outcomeId") UUID outcomeId,
            @Param("playerId") UUID playerId);
}
