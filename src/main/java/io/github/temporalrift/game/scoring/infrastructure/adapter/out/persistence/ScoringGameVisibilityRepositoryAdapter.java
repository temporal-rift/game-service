package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;

@Component
class ScoringGameVisibilityRepositoryAdapter implements ScoringGameVisibilityRepository {

    private final ScoringGameVisibilityJpaRepository jpaRepository;
    private final Clock clock;

    ScoringGameVisibilityRepositoryAdapter(ScoringGameVisibilityJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public boolean areFactionsRevealed(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        return jpaRepository
                .findById(gameId)
                .map(ScoringGameVisibilityJpaEntity::isFactionsRevealed)
                .orElse(false);
    }

    @Override
    public void markFactionsRevealed(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        jpaRepository.upsertRevealed(gameId, clock.instant());
    }
}
