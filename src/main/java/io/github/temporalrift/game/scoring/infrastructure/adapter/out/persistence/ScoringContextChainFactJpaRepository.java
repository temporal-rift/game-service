package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextChainFactJpaRepository extends JpaRepository<ScoringContextChainFactJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ScoringContextChainFactJpaEntity f where f.gameId = :gameId and f.consumed = false")
    List<ScoringContextChainFactJpaEntity> findAllByGameIdAndConsumedFalseWithLock(@Param("gameId") UUID gameId);
}
