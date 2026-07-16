package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ActionRoundJpaRepository extends JpaRepository<ActionRoundJpaEntity, UUID> {

    Optional<ActionRoundJpaEntity> findByGameIdAndEraNumberAndRoundNumber(UUID gameId, int eraNumber, int roundNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select round from ActionRoundJpaEntity round "
            + "where round.gameId = :gameId and round.eraNumber = :eraNumber and round.roundNumber = :roundNumber")
    Optional<ActionRoundJpaEntity> findByGameIdAndEraNumberAndRoundNumberWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber, @Param("roundNumber") int roundNumber);
}
