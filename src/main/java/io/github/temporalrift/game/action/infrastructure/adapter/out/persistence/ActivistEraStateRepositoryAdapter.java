package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.shared.CardType;

@Component
class ActivistEraStateRepositoryAdapter implements ActivistEraStateRepository {

    private final ActivistEraStateJpaRepository jpaRepository;

    ActivistEraStateRepositoryAdapter(ActivistEraStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ActivistEraState save(ActivistEraState state) {
        jpaRepository.save(toEntity(state));
        return state;
    }

    @Override
    public Optional<ActivistEraState> findByGameIdAndEraNumberAndActivistPlayerId(
            UUID gameId, int eraNumber, UUID activistPlayerId) {
        return jpaRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(gameId, eraNumber, activistPlayerId)
                .map(this::toDomain);
    }

    @Override
    public List<ActivistEraState> findDeclaredByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findAllByGameIdAndEraNumberAndDeclarationModeIsNotNull(gameId, eraNumber).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ActivistEraState> findExposedByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findAllByGameIdAndEraNumberAndExposedPlayerIdIsNotNull(gameId, eraNumber).stream()
                .map(this::toDomain)
                .toList();
    }

    private ActivistEraStateJpaEntity toEntity(ActivistEraState state) {
        var entity = new ActivistEraStateJpaEntity();
        entity.setId(state.id());
        entity.setGameId(state.gameId());
        entity.setEraNumber(state.eraNumber());
        entity.setActivistPlayerId(state.activistPlayerId());
        entity.setMomentumEligible(state.momentumEligible());
        entity.setDeclarationSucceeded(state.declarationSucceeded());
        entity.setDeclarationMode(
                state.declarationMode() == null ? null : state.declarationMode().name());
        entity.setTargetEventId(state.targetEventId());
        entity.setTargetOutcomeId(state.targetOutcomeId());
        entity.setExposedPlayerId(state.exposedPlayerId());
        if (state.exposedSignature() != null) {
            entity.setExposedSignatureType(state.exposedSignature().type().name());
            entity.setExposedSignatureEventId(state.exposedSignature().targetEventId());
            entity.setExposedSignatureSourceOutcomeId(state.exposedSignature().sourceOutcomeId());
            entity.setExposedSignatureTargetOutcomeId(state.exposedSignature().targetOutcomeId());
        }
        entity.setExposeBehaviorChanged(state.exposeBehaviorChanged());
        return entity;
    }

    private ActivistEraState toDomain(ActivistEraStateJpaEntity entity) {
        return ActivistEraState.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getEraNumber(),
                entity.getActivistPlayerId(),
                new ActivistEraState.PersistedState(
                        entity.isMomentumEligible(),
                        new ActivistEraState.Declaration(
                                entity.isDeclarationSucceeded(),
                                entity.getDeclarationMode() == null
                                        ? null
                                        : ActivistDeclarationMode.valueOf(entity.getDeclarationMode()),
                                entity.getTargetEventId(),
                                entity.getTargetOutcomeId()),
                        new ActivistEraState.Expose(
                                entity.getExposedPlayerId(),
                                entity.getExposedSignatureType() == null
                                        ? null
                                        : new ProbabilityInfluenceSignature(
                                                CardType.valueOf(entity.getExposedSignatureType()),
                                                entity.getExposedSignatureEventId(),
                                                entity.getExposedSignatureSourceOutcomeId(),
                                                entity.getExposedSignatureTargetOutcomeId()),
                                entity.isExposeBehaviorChanged())));
    }
}
