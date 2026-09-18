package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ForesightRevealJpaRepository extends JpaRepository<ForesightRevealJpaEntity, UUID> {
    boolean existsByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId);

    Optional<ForesightRevealJpaEntity> findByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId);
}
