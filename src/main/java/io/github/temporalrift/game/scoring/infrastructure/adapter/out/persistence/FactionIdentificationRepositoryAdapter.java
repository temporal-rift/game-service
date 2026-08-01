package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.port.out.FactionIdentificationRepository;

@Component
class FactionIdentificationRepositoryAdapter implements FactionIdentificationRepository {

    private final ScoringFactionIdentificationJpaRepository jpaRepository;

    FactionIdentificationRepositoryAdapter(ScoringFactionIdentificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean wasIdentifiedBeforeGameEnd(UUID gameId, UUID playerId) {
        return jpaRepository.existsByGameIdAndPlayerId(gameId, playerId);
    }

    @Override
    @Transactional
    public void recordIdentification(UUID gameId, UUID playerId) {
        jpaRepository.insertIfAbsent(UUID.randomUUID(), gameId, playerId);
    }
}
