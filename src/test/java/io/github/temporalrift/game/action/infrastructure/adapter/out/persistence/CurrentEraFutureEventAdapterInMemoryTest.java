package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.EventDefinition;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.OutcomeDefinition;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryTransactionManager;

/** The era definitions adapter replaces a game era wholesale, outcomes included, over the in-memory store. */
class CurrentEraFutureEventAdapterInMemoryTest {

    @Test
    void replacingAnEraRemovesThePriorDefinitionsAndOutcomes() {
        var database = new InMemoryDatabase(Duration.ofSeconds(1));
        var transactions = new TransactionTemplate(new InMemoryTransactionManager(database));
        var repository = new InMemoryFutureEventDefinitionJpaRepository(database);
        var adapter = new CurrentEraFutureEventAdapter(repository);
        var gameId = UUID.randomUUID();
        var oldEvent = new EventDefinition(UUID.randomUUID(), List.of(new OutcomeDefinition(UUID.randomUUID(), 40)));
        var newEvent = new EventDefinition(UUID.randomUUID(), List.of(new OutcomeDefinition(UUID.randomUUID(), 85)));

        transactions.executeWithoutResult(status -> adapter.replaceForGameEra(gameId, 1, List.of(oldEvent)));
        transactions.executeWithoutResult(status -> adapter.replaceForGameEra(gameId, 1, List.of(newEvent)));

        assertThat(adapter.findByGameIdAndEraNumber(gameId, 1)).containsExactly(newEvent);
        assertThat(adapter.findByGameIdAndEraNumber(gameId, 2)).isEmpty();
    }

    @Test
    void deletingOnlyTheOutcomesKeepsTheDefinitionsWithoutOutcomes() {
        var database = new InMemoryDatabase(Duration.ofSeconds(1));
        var repository = new InMemoryFutureEventDefinitionJpaRepository(database);
        var adapter = new CurrentEraFutureEventAdapter(repository);
        var gameId = UUID.randomUUID();
        var event = new EventDefinition(UUID.randomUUID(), List.of(new OutcomeDefinition(UUID.randomUUID(), 40)));
        adapter.replaceForGameEra(gameId, 1, List.of(event));

        repository.deleteOutcomesByGameIdAndEraNumber(gameId, 1);

        assertThat(adapter.findByGameIdAndEraNumber(gameId, 1))
                .containsExactly(new EventDefinition(event.eventId(), List.of()));
    }
}
