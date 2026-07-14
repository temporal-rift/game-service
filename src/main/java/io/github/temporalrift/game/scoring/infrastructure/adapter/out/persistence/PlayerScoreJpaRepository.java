package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlayerScoreJpaRepository extends JpaRepository<PlayerScoreJpaEntity, UUID> {

    List<PlayerScoreJpaEntity> findAllByGameId(UUID gameId);

    Optional<PlayerScoreJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlayerScoreJpaEntity p where p.gameId = :gameId")
    List<PlayerScoreJpaEntity> findAllByGameIdWithLock(@Param("gameId") UUID gameId);

    @Query(value = """
                    INSERT INTO player_score (id, game_id, player_id, faction, total_score)
                    VALUES (:id, :gameId, :playerId, :faction, :totalScore)
                    ON CONFLICT (game_id, player_id)
                    DO UPDATE SET faction = EXCLUDED.faction, total_score = EXCLUDED.total_score
                    RETURNING id
                    """, nativeQuery = true)
    UUID upsert(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("playerId") UUID playerId,
            @Param("faction") String faction,
            @Param("totalScore") int totalScore);
}
