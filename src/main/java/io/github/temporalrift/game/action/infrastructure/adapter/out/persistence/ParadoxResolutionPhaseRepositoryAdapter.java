package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;

@Component
class ParadoxResolutionPhaseRepositoryAdapter implements ParadoxResolutionPhaseRepository {

    private final ParadoxResolutionPhaseJpaRepository jpaRepository;

    ParadoxResolutionPhaseRepositoryAdapter(ParadoxResolutionPhaseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ParadoxResolutionPhase save(ParadoxResolutionPhase phase) {
        jpaRepository.save(toEntity(phase));
        return phase;
    }

    @Override
    public Optional<ParadoxResolutionPhase> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findByGameIdAndEraNumber(gameId, eraNumber).map(this::toDomain);
    }

    @Override
    public Optional<ParadoxResolutionPhase> findByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber) {
        return jpaRepository.findByGameIdAndEraNumberWithLock(gameId, eraNumber).map(this::toDomain);
    }

    private ParadoxResolutionPhaseJpaEntity toEntity(ParadoxResolutionPhase phase) {
        var entity = new ParadoxResolutionPhaseJpaEntity();
        entity.setId(phase.id());
        entity.setGameId(phase.gameId());
        entity.setEraNumber(phase.eraNumber());
        entity.setStatus(phase.status().name());
        entity.setExpiresAt(phase.expiresAt());
        entity.setSubmittedPlayerIds(phase.submittedPlayerIds().toArray(UUID[]::new));
        return entity;
    }

    private ParadoxResolutionPhase toDomain(ParadoxResolutionPhaseJpaEntity entity) {
        return ParadoxResolutionPhase.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getEraNumber(),
                entity.getExpiresAt(),
                ParadoxResolutionPhaseStatus.valueOf(entity.getStatus()),
                new LinkedHashSet<>(Arrays.asList(entity.getSubmittedPlayerIds())));
    }
}
