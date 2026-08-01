package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringEndGameScoreFactJpaRepository extends JpaRepository<ScoringEndGameScoreFactJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO scoring_end_game_score_fact (id, game_id, player_id, reason)
            VALUES (:id, :gameId, :playerId, :reason)
            ON CONFLICT (game_id, player_id, reason) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("playerId") UUID playerId,
            @Param("reason") String reason);
}
