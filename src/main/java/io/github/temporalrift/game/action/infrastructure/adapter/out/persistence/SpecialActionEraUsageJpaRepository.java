package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpecialActionEraUsageJpaRepository extends JpaRepository<SpecialActionEraUsageJpaEntity, UUID> {

    Optional<SpecialActionEraUsageJpaEntity> findByGameIdAndEraNumberAndPlayerId(
            UUID gameId, int eraNumber, UUID playerId);
}
