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
}
