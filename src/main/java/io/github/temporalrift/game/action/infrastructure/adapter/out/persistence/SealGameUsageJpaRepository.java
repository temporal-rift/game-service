package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SealGameUsageJpaRepository extends JpaRepository<SealGameUsageJpaEntity, UUID> {

    Optional<SealGameUsageJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId);
}
