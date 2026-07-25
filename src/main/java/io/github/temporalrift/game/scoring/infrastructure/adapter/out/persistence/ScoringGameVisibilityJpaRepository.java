package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringGameVisibilityJpaRepository extends JpaRepository<ScoringGameVisibilityJpaEntity, UUID> {

    @Modifying
    @Query(value = """
                    INSERT INTO scoring_game_visibility (game_id, factions_revealed, updated_at)
                    VALUES (:gameId, true, :updatedAt)
                    ON CONFLICT (game_id)
                    DO UPDATE SET factions_revealed = true, updated_at = :updatedAt
                    """, nativeQuery = true)
    void upsertRevealed(@Param("gameId") UUID gameId, @Param("updatedAt") Instant updatedAt);
}
