package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScoringContextChainFactJpaRepository extends JpaRepository<ScoringContextChainFactJpaEntity, UUID> {

    List<ScoringContextChainFactJpaEntity> findAllByGameIdAndConsumedFalse(UUID gameId);
}
