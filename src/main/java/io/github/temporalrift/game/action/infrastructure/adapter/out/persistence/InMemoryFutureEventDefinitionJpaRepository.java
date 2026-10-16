package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryFutureEventDefinitionJpaRepository extends InMemoryJpaRepository<FutureEventDefinitionJpaEntity, UUID>
        implements FutureEventDefinitionJpaRepository {

    InMemoryFutureEventDefinitionJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, FutureEventDefinitionJpaEntity>of(
                                "action_future_event_definition", FutureEventDefinitionJpaEntity::getId)
                        .unique(
                                "game_era_event",
                                definition -> List.of(
                                        definition.getGameId(), definition.getEraNumber(), definition.getEventId())));
    }

    @Override
    public List<FutureEventDefinitionJpaEntity> findAllByGameIdAndEraNumberOrderByDisplayOrder(
            UUID gameId, int eraNumber) {
        return findMatching(
                definition -> definition.getGameId().equals(gameId) && definition.getEraNumber() == eraNumber,
                Comparator.comparingInt(FutureEventDefinitionJpaEntity::getDisplayOrder));
    }

    @Override
    public void deleteOutcomesByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        findMatching(definition -> definition.getGameId().equals(gameId) && definition.getEraNumber() == eraNumber)
                .forEach(definition -> {
                    definition.setOutcomes(new ArrayList<>());
                    save(definition);
                });
    }

    @Override
    public void deleteDefinitionsByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        deleteMatching(definition -> definition.getGameId().equals(gameId) && definition.getEraNumber() == eraNumber);
    }
}
