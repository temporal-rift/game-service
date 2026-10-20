package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.simulation.domain.port.out.GameplayDiscard;

@Component
@Profile("in-memory")
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class InMemoryGameplayDiscard implements GameplayDiscard {

    private final InMemoryDatabase database;

    InMemoryGameplayDiscard(InMemoryDatabase database) {
        this.database = database;
    }

    @Override
    public void discardAll() {
        database.deleteAllRowsOf(table -> !table.startsWith("simulation_"));
    }
}
