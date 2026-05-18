package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ActionRoundSagaStateJpaRepository extends JpaRepository<ActionRoundSagaStateJpaEntity, UUID> {

    Optional<ActionRoundSagaStateJpaEntity> findByGameIdAndEraNumberAndRoundNumber(
            UUID gameId, int eraNumber, int roundNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ActionRoundSagaStateJpaEntity s "
            + "WHERE s.gameId = :gameId AND s.eraNumber = :eraNumber AND s.roundNumber = :roundNumber")
    Optional<ActionRoundSagaStateJpaEntity> findByGameIdAndEraNumberAndRoundNumberWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber, @Param("roundNumber") int roundNumber);

    @Query("SELECT s FROM ActionRoundSagaStateJpaEntity s WHERE s.status = 'WAITING'")
    List<ActionRoundSagaStateJpaEntity> findAllWaiting();

    @Query("SELECT s FROM ActionRoundSagaStateJpaEntity s WHERE s.status = 'CLOSING'")
    List<ActionRoundSagaStateJpaEntity> findAllClosing();
}
