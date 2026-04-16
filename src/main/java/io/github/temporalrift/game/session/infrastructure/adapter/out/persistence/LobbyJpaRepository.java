package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LobbyJpaRepository extends JpaRepository<LobbyJpaEntity, UUID> {

    Optional<LobbyJpaEntity> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);
}
