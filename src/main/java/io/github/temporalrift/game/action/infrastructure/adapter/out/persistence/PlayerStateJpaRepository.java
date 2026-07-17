package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlayerStateJpaRepository extends JpaRepository<PlayerStateJpaEntity, UUID> {

    Optional<PlayerStateJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from PlayerStateJpaEntity state "
            + "where state.gameId = :gameId and state.playerId = :playerId")
    Optional<PlayerStateJpaEntity> findByGameIdAndPlayerIdWithLock(
            @Param("gameId") UUID gameId, @Param("playerId") UUID playerId);

    List<PlayerStateJpaEntity> findAllByGameId(UUID gameId);
}
