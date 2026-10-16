package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.shared.domain.event.ScoresUpdated;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryEraSagaScoresUpdatedInboxJpaRepository
        extends InMemoryJpaRepository<EraSagaScoresUpdatedInboxJpaEntity, UUID>
        implements EraSagaScoresUpdatedInboxJpaRepository {

    private static final String WAITING_SCORES = "WAITING_SCORES";

    private final EraSagaStateJpaRepository sagas;
    private final ObjectMapper objectMapper;

    InMemoryEraSagaScoresUpdatedInboxJpaRepository(
            InMemoryDatabase database, EraSagaStateJpaRepository sagas, ObjectMapper objectMapper) {
        super(
                database,
                TableDefinition.<UUID, EraSagaScoresUpdatedInboxJpaEntity>of(
                                "era_saga_scores_updated_inbox", EraSagaScoresUpdatedInboxJpaEntity::getId)
                        .unique("game_era", inbox -> List.of(inbox.getGameId(), inbox.getEraNumber())));
        this.sagas = sagas;
        this.objectMapper = objectMapper;
    }

    @Override
    public int insertIfAbsent(UUID id, UUID gameId, int eraNumber, String payload) {
        var inbox = EraSagaScoresUpdatedInboxJpaEntity.restore(
                id, gameId, eraNumber, objectMapper.readValue(payload, ScoresUpdated.class));
        return insertRowIfAbsent(inbox) ? 1 : 0;
    }

    @Override
    public List<EraSagaScoresUpdatedInboxJpaEntity> findRecordedButNotAdvanced() {
        return findMatching(inbox -> sagas.findById(inbox.getGameId())
                .filter(saga -> saga.getEraNumber() == inbox.getEraNumber())
                .filter(saga -> WAITING_SCORES.equals(saga.getStatus()))
                .isPresent());
    }
}
