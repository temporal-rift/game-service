package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;

@Component
class ScoringPlayerRepositoryAdapter implements ScoringPlayerRepository {

    private final ScoringPlayerJpaRepository jpaRepository;

    ScoringPlayerRepositoryAdapter(ScoringPlayerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void upsertPlayerName(UUID gameId, UUID playerId, String playerName) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(playerName, "playerName must not be null");
        jpaRepository.upsert(UUID.randomUUID(), gameId, playerId, playerName);
    }
}
