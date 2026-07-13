package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScoringContextEraOutcomeExpectationJpaRepository
        extends JpaRepository<ScoringContextEraOutcomeExpectationJpaEntity, UUID> {

    Optional<ScoringContextEraOutcomeExpectationJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber);
}
