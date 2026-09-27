package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextParadoxCascadeFactJpaRepository
        extends JpaRepository<ScoringContextParadoxCascadeFactJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ScoringContextParadoxCascadeFactJpaEntity f where f.gameId = :gameId and f.consumed = false")
    List<ScoringContextParadoxCascadeFactJpaEntity> findAllByGameIdAndConsumedFalseWithLock(
            @Param("gameId") UUID gameId);

    // Distinct events already penalized in an earlier era, so this era's evaluator does not pay the
    // PARADOX_CASCADE_PENALTY again for one that's still cascading.
    @Query("select distinct f.affectedEventId from ScoringContextParadoxCascadeFactJpaEntity f "
            + "where f.gameId = :gameId and f.consumed = true")
    List<UUID> findDistinctConsumedAffectedEventIds(@Param("gameId") UUID gameId);
}
