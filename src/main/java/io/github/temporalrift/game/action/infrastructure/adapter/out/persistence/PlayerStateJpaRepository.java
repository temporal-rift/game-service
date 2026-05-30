package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlayerStateJpaRepository extends JpaRepository<PlayerStateJpaEntity, UUID> {

    Optional<PlayerStateJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    List<PlayerStateJpaEntity> findAllByGameId(UUID gameId);
}
