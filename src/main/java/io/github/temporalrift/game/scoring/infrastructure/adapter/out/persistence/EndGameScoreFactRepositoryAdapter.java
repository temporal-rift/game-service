package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EndGameScoreFactRepository;

@Component
class EndGameScoreFactRepositoryAdapter implements EndGameScoreFactRepository {

    private final ScoringEndGameScoreFactJpaRepository jpaRepository;

    EndGameScoreFactRepositoryAdapter(ScoringEndGameScoreFactJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public boolean claim(UUID gameId, UUID playerId, ScoreReason reason) {
        return jpaRepository.insertIfAbsent(UUID.randomUUID(), gameId, playerId, reason.name()) == 1;
    }
}
