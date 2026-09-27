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

    // Two Erasers can erase the same outcome in one round, so each acting player keeps their own row.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_annihilated_outcome
                        (id, game_id, era_number, event_id, outcome_id, player_id, erased, was_leading)
                    VALUES (:id, :gameId, :eraNumber, :eventId, :outcomeId, :playerId, :erased, :wasLeading)
                    ON CONFLICT (game_id, era_number, event_id, outcome_id, player_id) DO NOTHING
                    """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("eventId") UUID eventId,
            @Param("outcomeId") UUID outcomeId,
            @Param("playerId") UUID playerId,
            @Param("erased") boolean erased,
            @Param("wasLeading") boolean wasLeading);
}
