package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.shared.ScoresUpdated;

@Component
class EraSagaScoresUpdatedInboxRepositoryAdapter implements EraSagaScoresUpdatedInboxRepository {

    private final EraSagaScoresUpdatedInboxJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    EraSagaScoresUpdatedInboxRepositoryAdapter(
            EraSagaScoresUpdatedInboxJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(ScoresUpdated event) {
        // Native ON CONFLICT DO NOTHING, not check-then-insert: a failed statement inside this
        // @Transactional(REQUIRES_NEW) handler aborts the whole transaction even if the Java exception
        // is caught, poisoning the era-saga transition attempt that follows in the same call.
        jpaRepository.insertIfAbsent(
                UUID.randomUUID(), event.gameId(), event.eraNumber(), objectMapper.writeValueAsString(event));
    }

    @Override
    public List<ScoresUpdated> findRecordedButNotAdvanced() {
        return jpaRepository.findRecordedButNotAdvanced().stream()
                .map(EraSagaScoresUpdatedInboxJpaEntity::toDomain)
                .toList();
    }
}
