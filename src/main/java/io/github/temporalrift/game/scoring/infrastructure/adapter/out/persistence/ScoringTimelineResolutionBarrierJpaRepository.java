package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

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
}
