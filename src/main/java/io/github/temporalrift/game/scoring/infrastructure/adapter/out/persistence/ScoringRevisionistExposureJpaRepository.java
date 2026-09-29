package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringRevisionistExposureJpaRepository extends JpaRepository<ScoringRevisionistExposureJpaEntity, UUID> {

    @Query("SELECT e.playerId FROM ScoringRevisionistExposureJpaEntity e WHERE e.gameId = :gameId")
    List<UUID> findPlayerIdsByGameId(@Param("gameId") UUID gameId);

    @Modifying
    @Query(value = """
            INSERT INTO scoring_revisionist_exposure (id, game_id, player_id)
            VALUES (:id, :gameId, :playerId)
            ON CONFLICT (game_id, player_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("gameId") UUID gameId, @Param("playerId") UUID playerId);
}
