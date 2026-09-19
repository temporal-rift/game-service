package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.SealGameUsageRepository;
import io.github.temporalrift.game.action.domain.specialactionerausage.SealGameUsage;

@Component
class SealGameUsageRepositoryAdapter implements SealGameUsageRepository {

    private final SealGameUsageJpaRepository jpaRepository;

    SealGameUsageRepositoryAdapter(SealGameUsageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SealGameUsage save(SealGameUsage usage) {
        var entity = new SealGameUsageJpaEntity();
        entity.setId(usage.id());
        entity.setGameId(usage.gameId());
        entity.setPlayerId(usage.playerId());
        entity.setAcceptedUses(usage.acceptedUses());
        jpaRepository.save(entity);
        return usage;
    }

    @Override
    public Optional<SealGameUsage> findByGameIdAndPlayerId(UUID gameId, UUID playerId) {
        return jpaRepository
                .findByGameIdAndPlayerId(gameId, playerId)
                .map(entity -> SealGameUsage.reconstitute(
                        entity.getId(), entity.getGameId(), entity.getPlayerId(), entity.getAcceptedUses()));
    }
}
