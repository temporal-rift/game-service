package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringFactionIdentificationJpaRepository extends JpaRepository<ScoringFactionIdentificationJpaEntity, UUID> {

    boolean existsByGameIdAndPlayerId(UUID gameId, UUID playerId);

    @Modifying
    @Query(value = """
            INSERT INTO scoring_faction_identification (id, game_id, player_id)
            VALUES (:id, :gameId, :playerId)
            ON CONFLICT (game_id, player_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("gameId") UUID gameId, @Param("playerId") UUID playerId);
}
