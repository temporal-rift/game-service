package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerReconnectSagaStateJpaRepository extends JpaRepository<PlayerReconnectSagaStateJpaEntity, UUID> {

    Optional<PlayerReconnectSagaStateJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    List<PlayerReconnectSagaStateJpaEntity> findAllByStatus(String status);

    long countByGameIdAndStatus(UUID gameId, String status);
}
