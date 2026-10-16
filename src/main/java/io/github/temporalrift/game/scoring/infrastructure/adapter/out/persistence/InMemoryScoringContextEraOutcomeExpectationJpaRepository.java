package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextEraOutcomeExpectationJpaRepository
        extends InMemoryJpaRepository<ScoringContextEraOutcomeExpectationJpaEntity, UUID>
        implements ScoringContextEraOutcomeExpectationJpaRepository {

    InMemoryScoringContextEraOutcomeExpectationJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextEraOutcomeExpectationJpaEntity>of(
                                "scoring_context_era_outcome_expectation",
                                ScoringContextEraOutcomeExpectationJpaEntity::getId)
                        .unique("game_era", expectation -> key(expectation.getGameId(), expectation.getEraNumber())));
    }

    @Override
    public Optional<ScoringContextEraOutcomeExpectationJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findFirst(
                expectation -> expectation.getGameId().equals(gameId) && expectation.getEraNumber() == eraNumber);
    }

    @Override
    public void upsert(UUID id, UUID gameId, int eraNumber, int expectedOutcomeCount) {
        lockForUpsert(key("scoring_context_era_outcome_expectation", gameId, eraNumber));
        var expectation = findByGameIdAndEraNumber(gameId, eraNumber).orElseGet(() -> {
            var created = new ScoringContextEraOutcomeExpectationJpaEntity();
            created.setId(id);
            created.setGameId(gameId);
            created.setEraNumber(eraNumber);
            return created;
        });
        expectation.setExpectedOutcomeCount(expectedOutcomeCount);
        save(expectation);
    }
}
