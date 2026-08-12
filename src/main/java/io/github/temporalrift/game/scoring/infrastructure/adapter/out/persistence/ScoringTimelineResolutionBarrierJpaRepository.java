package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringTimelineResolutionBarrierJpaRepository
        extends JpaRepository<ScoringTimelineResolutionBarrierJpaEntity, UUID> {
    Optional<ScoringTimelineResolutionBarrierJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber);

    @Modifying
    @Query(value = """
                    INSERT INTO scoring_timeline_resolution_barrier (id, game_id, era_number, payload)
                    VALUES (:id, :gameId, :eraNumber, CAST(:payload AS jsonb))
                    ON CONFLICT (game_id, era_number) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("payload") String payload);

    /**
     * Eras whose resolution barrier landed but whose scoring never durably completed — the sweep's
     * candidate set. A concurrent signal race (each of {@code EraScoringCompletionChecker}'s several
     * independent triggers only rechecks completion once, right after its own write) can otherwise
     * leave completion permanently unattempted if two triggers interleave so that neither's read sees
     * the other's just-committed write in time.
     */
    @Query(value = """
                    SELECT b.game_id AS gameId, b.era_number AS eraNumber
                    FROM scoring_timeline_resolution_barrier b
                    LEFT JOIN scoring_era_completion c
                        ON c.game_id = b.game_id AND c.era_number = b.era_number
                    WHERE c.game_id IS NULL
                    """, nativeQuery = true)
    List<PendingCompletion> findResolvedErasNotYetScored();

    interface PendingCompletion {
        UUID getGameId();

        int getEraNumber();
    }
}
