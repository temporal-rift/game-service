package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;
import io.github.temporalrift.game.session.domain.port.out.ForesightRevealRepository;

@Component
class ForesightRevealRepositoryAdapter implements ForesightRevealRepository {

    private final ForesightRevealJpaRepository repository;

    ForesightRevealRepositoryAdapter(ForesightRevealJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(ForesightReveal reveal) {
        if (repository.existsByGameIdAndEraNumberAndPlayerId(reveal.gameId(), reveal.eraNumber(), reveal.playerId())) {
            return false;
        }
        try {
            repository.save(ForesightRevealJpaEntity.from(
                    reveal.gameId(),
                    reveal.eraNumber(),
                    reveal.playerId(),
                    reveal.nextEraNumber(),
                    reveal.catalogEventIds(),
                    reveal.emptyReason()));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Override
    public Optional<ForesightReveal> findByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId) {
        return repository
                .findByGameIdAndEraNumberAndPlayerId(gameId, eraNumber, playerId)
                .map(entity -> new ForesightReveal(
                        entity.gameId(),
                        entity.eraNumber(),
                        entity.playerId(),
                        entity.nextEraNumber(),
                        entity.catalogEventIds(),
                        entity.emptyReason()));
    }
}
