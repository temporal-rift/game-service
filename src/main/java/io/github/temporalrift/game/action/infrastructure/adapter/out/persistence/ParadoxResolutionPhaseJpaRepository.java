package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ParadoxResolutionPhaseJpaRepository extends JpaRepository<ParadoxResolutionPhaseJpaEntity, UUID> {

    Optional<ParadoxResolutionPhaseJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select phase from ParadoxResolutionPhaseJpaEntity phase "
            + "where phase.gameId = :gameId and phase.eraNumber = :eraNumber")
    Optional<ParadoxResolutionPhaseJpaEntity> findByGameIdAndEraNumberWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);
}
