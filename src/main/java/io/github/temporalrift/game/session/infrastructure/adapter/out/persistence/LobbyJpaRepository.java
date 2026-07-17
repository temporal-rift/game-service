package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LobbyJpaRepository extends JpaRepository<LobbyJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LobbyJpaEntity l WHERE l.id = :id")
    Optional<LobbyJpaEntity> findByIdWithLock(@Param("id") UUID id);

    Optional<LobbyJpaEntity> findByJoinCode(String joinCode);
}
