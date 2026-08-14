package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HandSelectionJpaRepository extends JpaRepository<HandSelectionJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select selection from HandSelectionJpaEntity selection "
            + "where selection.gameId = :gameId and selection.eraNumber = :eraNumber "
            + "and selection.playerId = :playerId")
    Optional<HandSelectionJpaEntity> findWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber, @Param("playerId") UUID playerId);

    @Query("select selection.id from HandSelectionJpaEntity selection "
            + "where selection.status = 'OPEN' and selection.selectionExpiresAt <= :now")
    List<UUID> findOpenDueIds(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select selection from HandSelectionJpaEntity selection where selection.id = :id")
    Optional<HandSelectionJpaEntity> findByIdWithLock(@Param("id") UUID id);
}
