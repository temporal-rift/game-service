package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GameJpaRepository extends JpaRepository<GameJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GameJpaEntity g WHERE g.id = :id")
    Optional<GameJpaEntity> findByIdWithLock(@Param("id") UUID id);
}
