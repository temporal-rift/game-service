package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.port.out.FactionDisclosureRepository;

@Component
class FactionDisclosureRepositoryAdapter implements FactionDisclosureRepository {

    private final ScoringFactionDisclosureJpaRepository jpaRepository;

    FactionDisclosureRepositoryAdapter(ScoringFactionDisclosureJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void recordDisclosure(UUID gameId, UUID playerId) {
        jpaRepository.insertIfAbsent(UUID.randomUUID(), gameId, playerId);
    }

    @Override
    public Set<UUID> disclosedPlayerIds(UUID gameId) {
        return Set.copyOf(jpaRepository.findPlayerIdsByGameId(gameId));
    }
}
