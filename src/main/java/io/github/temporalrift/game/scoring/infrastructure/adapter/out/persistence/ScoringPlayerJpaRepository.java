package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringPlayerJpaRepository extends JpaRepository<ScoringPlayerJpaEntity, UUID> {

    List<ScoringPlayerJpaEntity> findAllByGameId(UUID gameId);

    @Modifying
    @Query(value = """
                    INSERT INTO scoring_player (id, game_id, player_id, player_name)
                    VALUES (:id, :gameId, :playerId, :playerName)
                    ON CONFLICT (game_id, player_id)
                    DO UPDATE SET player_name = EXCLUDED.player_name
                    """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("playerId") UUID playerId,
            @Param("playerName") String playerName);
}
