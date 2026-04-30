package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EraSagaStateJpaRepository extends JpaRepository<EraSagaStateJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EraSagaStateJpaEntity s WHERE s.gameId = :gameId")
    Optional<EraSagaStateJpaEntity> findByGameIdWithLock(@Param("gameId") UUID gameId);
}
