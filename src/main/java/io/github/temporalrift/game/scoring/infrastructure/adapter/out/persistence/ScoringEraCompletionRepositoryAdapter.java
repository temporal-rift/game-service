package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.playerscore.InvalidScoreEraException;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;

@Component
class ScoringEraCompletionRepositoryAdapter implements ScoringEraCompletionRepository {

    private final ScoringEraCompletionJpaRepository jpaRepository;

    ScoringEraCompletionRepositoryAdapter(ScoringEraCompletionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryMarkScoringComplete(UUID gameId, int eraNumber) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        if (eraNumber < 1) {
            throw new InvalidScoreEraException(eraNumber);
        }
        return jpaRepository.insertIfAbsent(gameId, eraNumber) == 1;
    }
}
