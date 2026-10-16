package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryTable;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;
import io.github.temporalrift.game.simulation.domain.port.out.EntropyDecisionRepository;

@Component
@Profile("in-memory")
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class InMemoryEntropyDecisionRepository implements EntropyDecisionRepository {

    private static final TableDefinition<Draw, Draw> DRAWS =
            TableDefinition.of("simulation_entropy_decision", draw -> draw);

    private final InMemoryTable<Draw, Draw> draws;

    InMemoryEntropyDecisionRepository(InMemoryDatabase database) {
        this.draws = database.table(DRAWS);
    }

    @Override
    public Optional<Long> find(String purpose, String coordinate, int drawIndex) {
        return draws.find(new Draw(purpose, coordinate, drawIndex, 0)).map(Draw::word);
    }

    @Override
    public void save(String purpose, String coordinate, int drawIndex, long word) {
        draws.insertIfAbsent(new Draw(purpose, coordinate, drawIndex, word));
    }

    private record Draw(String purpose, String coordinate, int drawIndex, long word) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Draw that
                    && purpose.equals(that.purpose)
                    && coordinate.equals(that.coordinate)
                    && drawIndex == that.drawIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(purpose, coordinate, drawIndex);
        }
    }
}
