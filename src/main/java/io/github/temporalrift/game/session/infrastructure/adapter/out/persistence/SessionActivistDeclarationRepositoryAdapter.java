package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.port.out.SessionActivistDeclarationRepository;
import io.github.temporalrift.game.shared.SpecialAction;

@Component
class SessionActivistDeclarationRepositoryAdapter implements SessionActivistDeclarationRepository {

    private final SessionActivistDeclarationJpaRepository repository;

    SessionActivistDeclarationRepositoryAdapter(SessionActivistDeclarationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void saveIfAbsent(UUID gameId, int eraNumber, UUID playerId, UUID targetEventId, SpecialAction mode) {
        if (!repository.existsByGameIdAndEraNumberAndPlayerId(gameId, eraNumber, playerId)) {
            repository.save(SessionActivistDeclarationJpaEntity.from(gameId, eraNumber, playerId, targetEventId, mode));
        }
    }

    @Override
    public List<UUID> findPlayerIdsTargeting(UUID gameId, int eraNumber, UUID targetEventId) {
        return repository.findAllByGameIdAndEraNumberAndTargetEventId(gameId, eraNumber, targetEventId).stream()
                .map(SessionActivistDeclarationJpaEntity::playerId)
                .toList();
    }
}
