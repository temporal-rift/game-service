package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextPlayerJpaRepository extends JpaRepository<ScoringContextPlayerJpaEntity, UUID> {

    List<ScoringContextPlayerJpaEntity> findAllByGameId(UUID gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select player from ScoringContextPlayerJpaEntity player "
            + "where player.gameId = :gameId and player.playerId = :playerId")
    Optional<ScoringContextPlayerJpaEntity> findByGameIdAndPlayerIdWithLock(
            @Param("gameId") UUID gameId, @Param("playerId") UUID playerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_player (id, game_id, player_id, faction)
                    VALUES (:id, :gameId, :playerId, :faction)
                    ON CONFLICT (game_id, player_id)
                    DO UPDATE SET faction = EXCLUDED.faction
                    """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("playerId") UUID playerId,
            @Param("faction") String faction);
}
