package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.port.out.RevisionistExposureRepository;

@Component
class RevisionistExposureRepositoryAdapter implements RevisionistExposureRepository {

    private final ScoringRevisionistExposureJpaRepository jpaRepository;

    RevisionistExposureRepositoryAdapter(ScoringRevisionistExposureJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void recordExposure(UUID gameId, UUID playerId) {
        jpaRepository.insertIfAbsent(UUID.randomUUID(), gameId, playerId);
    }

    @Override
    public Set<UUID> exposedPlayerIds(UUID gameId) {
        return Set.copyOf(jpaRepository.findPlayerIdsByGameId(gameId));
    }
}
