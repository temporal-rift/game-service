package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringEraCompletionJpaRepository
        extends JpaRepository<ScoringEraCompletionJpaEntity, ScoringEraCompletionJpaEntity.ScoringEraCompletionKey> {

    @Modifying
    @Query(value = """
                    INSERT INTO scoring_era_completion (game_id, era_number, completed_at)
                    VALUES (:gameId, :eraNumber, CURRENT_TIMESTAMP)
                    ON CONFLICT (game_id, era_number) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(@Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);
}
