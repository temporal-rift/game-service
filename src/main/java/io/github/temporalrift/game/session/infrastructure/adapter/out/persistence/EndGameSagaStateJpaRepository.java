package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EndGameSagaStateJpaRepository extends JpaRepository<EndGameSagaStateJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EndGameSagaStateJpaEntity s WHERE s.gameId = :gameId")
    Optional<EndGameSagaStateJpaEntity> findByGameIdWithLock(@Param("gameId") UUID gameId);

    // Native upsert, not save(): a manually-assigned @Id with no @Version can't detect concurrent
    // first-time inserts for the same gameId, so save() would silently overwrite instead of one losing.
    @Modifying
    @Query(value = """
                    INSERT INTO end_game_saga_state (game_id, trigger_type, status, player_ids)
                    VALUES (:gameId, :triggerType, :status, :playerIds)
                    ON CONFLICT (game_id) DO NOTHING
                    """, nativeQuery = true)
    int claimIfAbsent(
            @Param("gameId") UUID gameId,
            @Param("triggerType") String triggerType,
            @Param("status") String status,
            @Param("playerIds") UUID[] playerIds);
}
