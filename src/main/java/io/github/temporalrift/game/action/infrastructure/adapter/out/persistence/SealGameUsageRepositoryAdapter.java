package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.SealGameUsageRepository;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@Component
class SealGameUsageRepositoryAdapter implements SealGameUsageRepository {

    private final SpecialActionEraUsageJpaRepository eraUsageJpaRepository;

    SealGameUsageRepositoryAdapter(SpecialActionEraUsageJpaRepository eraUsageJpaRepository) {
        this.eraUsageJpaRepository = eraUsageJpaRepository;
    }

    @Override
    public int countAcceptedSeals(UUID gameId, UUID playerId) {
        return eraUsageJpaRepository.countErasClaiming(gameId, playerId, SpecialAction.SEAL.name());
    }
}
