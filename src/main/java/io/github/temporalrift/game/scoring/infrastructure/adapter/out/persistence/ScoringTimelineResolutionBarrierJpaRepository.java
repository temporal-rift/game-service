package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScoringTimelineResolutionBarrierJpaRepository
        extends JpaRepository<ScoringTimelineResolutionBarrierJpaEntity, UUID> {
    Optional<ScoringTimelineResolutionBarrierJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber);
}
