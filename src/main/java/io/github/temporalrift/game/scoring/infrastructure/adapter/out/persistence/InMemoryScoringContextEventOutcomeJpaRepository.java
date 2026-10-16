package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextEventOutcomeJpaRepository
        extends InMemoryJpaRepository<ScoringContextEventOutcomeJpaEntity, UUID>
        implements ScoringContextEventOutcomeJpaRepository {

    InMemoryScoringContextEventOutcomeJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextEventOutcomeJpaEntity>of(
                                "scoring_context_event_outcome", ScoringContextEventOutcomeJpaEntity::getId)
                        .unique(
                                "game_era_event",
                                outcome -> key(outcome.getGameId(), outcome.getEraNumber(), outcome.getEventId())));
    }

    @Override
    public List<ScoringContextEventOutcomeJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findMatching(outcome -> outcome.getGameId().equals(gameId) && outcome.getEraNumber() == eraNumber);
    }

    @Override
    public void insertWrittenOutcomeIfFirst(
            UUID id, UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId) {
        lockForUpsert(key("scoring_context_event_outcome", gameId, eraNumber, eventId));
        var outcome = findFirst(row -> row.getGameId().equals(gameId)
                        && row.getEraNumber() == eraNumber
                        && row.getEventId().equals(eventId))
                .orElseGet(() -> {
                    var created = new ScoringContextEventOutcomeJpaEntity();
                    created.setId(id);
                    created.setGameId(gameId);
                    created.setEraNumber(eraNumber);
                    created.setEventId(eventId);
                    return created;
                });
        if (outcome.getWrittenOutcomeId() == null) {
            outcome.setWrittenOutcomeId(outcomeId);
        }
        if (outcome.getWrittenByPlayerId() == null) {
            outcome.setWrittenByPlayerId(playerId);
        }
        save(outcome);
    }
}
