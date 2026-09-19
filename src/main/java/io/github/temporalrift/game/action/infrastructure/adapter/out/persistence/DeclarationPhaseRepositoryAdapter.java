package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;

@Component
class DeclarationPhaseRepositoryAdapter implements DeclarationPhaseRepository {

    private final DeclarationPhaseJpaRepository jpaRepository;

    DeclarationPhaseRepositoryAdapter(DeclarationPhaseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public DeclarationPhase save(DeclarationPhase phase) {
        jpaRepository.save(toEntity(phase));
        return phase;
    }

    @Override
    public boolean createIfAbsent(DeclarationPhase phase) {
        if (phase.status() != DeclarationPhaseStatus.OPEN) {
            throw new IllegalArgumentException("Only open declaration phases can be created");
        }
        return jpaRepository.insertIfAbsent(
                        phase.id(),
                        phase.gameId(),
                        phase.eraNumber(),
                        phase.status().name(),
                        phase.expiresAt())
                == 1;
    }

    @Override
    public Optional<DeclarationPhase> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findByGameIdAndEraNumber(gameId, eraNumber).map(this::toDomain);
    }

    @Override
    public Optional<DeclarationPhase> findByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber) {
        return jpaRepository.findByGameIdAndEraNumberWithLock(gameId, eraNumber).map(this::toDomain);
    }

    @Override
    public Optional<DeclarationPhase> findByIdWithLock(UUID id) {
        return jpaRepository.findByIdWithLock(id).map(this::toDomain);
    }

    @Override
    public List<UUID> findOpenDueIds(Instant now) {
        return jpaRepository.findOpenDueIds(now);
    }

    private DeclarationPhaseJpaEntity toEntity(DeclarationPhase phase) {
        var entity = new DeclarationPhaseJpaEntity();
        entity.setId(phase.id());
        entity.setGameId(phase.gameId());
        entity.setEraNumber(phase.eraNumber());
        entity.setStatus(phase.status().name());
        entity.setExpiresAt(phase.expiresAt());
        return entity;
    }

    private DeclarationPhase toDomain(DeclarationPhaseJpaEntity entity) {
        return DeclarationPhase.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getEraNumber(),
                entity.getExpiresAt(),
                DeclarationPhaseStatus.valueOf(entity.getStatus()));
    }
}
