package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.SpecialActionEraUsageRepository;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraUsage;
import io.github.temporalrift.game.shared.SpecialAction;

@Component
class SpecialActionEraUsageRepositoryAdapter implements SpecialActionEraUsageRepository {

    private final SpecialActionEraUsageJpaRepository jpaRepository;

    SpecialActionEraUsageRepositoryAdapter(SpecialActionEraUsageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SpecialActionEraUsage save(SpecialActionEraUsage usage) {
        jpaRepository.save(toEntity(usage));
        return usage;
    }

    @Override
    public Optional<SpecialActionEraUsage> findByGameIdAndEraNumberAndPlayerId(
            UUID gameId, int eraNumber, UUID playerId) {
        return jpaRepository
                .findByGameIdAndEraNumberAndPlayerId(gameId, eraNumber, playerId)
                .map(this::toDomain);
    }

    private SpecialActionEraUsageJpaEntity toEntity(SpecialActionEraUsage usage) {
        var entity = new SpecialActionEraUsageJpaEntity();
        entity.setId(usage.id());
        entity.setGameId(usage.gameId());
        entity.setEraNumber(usage.eraNumber());
        entity.setPlayerId(usage.playerId());
        entity.setClaimedSpecials(
                usage.claimedSpecials().stream().map(Enum::name).toList());
        return entity;
    }

    private SpecialActionEraUsage toDomain(SpecialActionEraUsageJpaEntity entity) {
        return SpecialActionEraUsage.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getEraNumber(),
                entity.getPlayerId(),
                entity.getClaimedSpecials().stream().map(SpecialAction::valueOf).collect(Collectors.toSet()));
    }
}
