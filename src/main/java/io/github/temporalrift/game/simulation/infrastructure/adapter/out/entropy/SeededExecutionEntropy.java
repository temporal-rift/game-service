package io.github.temporalrift.game.simulation.infrastructure.adapter.out.entropy;

import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;
import io.github.temporalrift.game.simulation.domain.execution.EntropyDerivation;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContext;
import io.github.temporalrift.game.simulation.domain.port.out.EntropyDecisionRepository;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class SeededExecutionEntropy implements ExecutionEntropy {

    private final PinnedExecutionContext pinned;
    private final EntropyDecisionRepository decisions;

    SeededExecutionEntropy(PinnedExecutionContext pinned, EntropyDecisionRepository decisions) {
        this.pinned = pinned;
        this.decisions = decisions;
    }

    @Override
    public RandomGenerator generator(EntropyPurpose purpose, EntropyCoordinate coordinate) {
        var context = pinned.require();
        return new RealizedRandom(context, purpose.name(), render(context, coordinate), decisions);
    }

    @Override
    public UUID identity(IdentityKind kind, EntropyCoordinate coordinate) {
        var context = pinned.require();
        return EntropyDerivation.identity(context, kind.name(), render(context, coordinate));
    }

    private static String render(ExecutionContext context, EntropyCoordinate coordinate) {
        return coordinate.render(playerId -> {
            var seat = context.seatOf(playerId);
            return seat == null ? playerId.toString() : "seat-" + seat.seatIndex();
        });
    }

    /** Each word is derived once, then read back from its durable record on every repetition of the same step. */
    private static final class RealizedRandom implements RandomGenerator {

        private final ExecutionContext context;
        private final String purpose;
        private final String coordinate;
        private final EntropyDecisionRepository decisions;
        private int drawIndex;

        private RealizedRandom(
                ExecutionContext context, String purpose, String coordinate, EntropyDecisionRepository decisions) {
            this.context = context;
            this.purpose = purpose;
            this.coordinate = coordinate;
            this.decisions = decisions;
        }

        @Override
        public long nextLong() {
            var index = drawIndex++;
            return decisions.find(purpose, coordinate, index).orElseGet(() -> {
                var word = EntropyDerivation.word(context, purpose, coordinate, index);
                decisions.save(purpose, coordinate, index, word);
                return word;
            });
        }
    }
}
