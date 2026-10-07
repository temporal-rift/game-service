package io.github.temporalrift.game.simulation.infrastructure.adapter.out.entropy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.port.out.EntropyDecisionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;

@ExtendWith(MockitoExtension.class)
class SeededExecutionEntropyTest {

    private static final EntropyCoordinate SEAT_0_ERA_1 =
            EntropyCoordinate.none().era(1).player(ExecutionContextTestData.PLAYER_0);

    @Mock
    ExecutionRepository executions;

    private final InMemoryDecisions decisions = new InMemoryDecisions();

    private SeededExecutionEntropy entropyFor(String seed) {
        given(executions.find()).willReturn(Optional.of(Execution.configure(ExecutionContextTestData.context(seed))));
        return new SeededExecutionEntropy(new PinnedExecutionContext(executions), decisions);
    }

    @Test
    @DisplayName("two clean executions with the same seed draw identical sequences for the same purpose and coordinate")
    void generator_sameSeedInCleanExecutions_drawsIdenticalSequences() {
        var first = draw(entropyFor("42"), 8);
        var second =
                draw(new SeededExecutionEntropy(new PinnedExecutionContext(executions), new InMemoryDecisions()), 8);

        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    @DisplayName("a repeated step reads its recorded words back instead of consuming new draws")
    void generator_repeatedStep_rereadsRecordedWordsWithoutNewDraws() {
        var entropy = entropyFor("42");
        var original = draw(entropy, 5);
        var recorded = decisions.size();

        var repeat = draw(entropy, 5);

        assertThat(repeat).containsExactlyElementsOf(original);
        assertThat(decisions.size()).isEqualTo(recorded).isEqualTo(5);
    }

    @Test
    @DisplayName("a recorded word wins over a fresh derivation, so recovery never changes an earlier choice")
    void generator_recordedWord_isAuthoritative() {
        var entropy = entropyFor("42");
        decisions.save(EntropyPurpose.CARD_DEAL.name(), "era=1;player=seat-0", 0, 1234L);

        assertThat(entropy.generator(EntropyPurpose.CARD_DEAL, SEAT_0_ERA_1).nextLong())
                .isEqualTo(1234L);
    }

    @Test
    @DisplayName("purposes and coordinates draw from independent streams")
    void generator_differentPurposeOrCoordinate_isIndependent() {
        var entropy = entropyFor("42");
        var deal = draw(entropy, 4);

        var otherPurpose = entropy.generator(EntropyPurpose.HAND_TIMEOUT_SELECTION, SEAT_0_ERA_1);
        var otherSeat = entropy.generator(
                EntropyPurpose.CARD_DEAL, EntropyCoordinate.none().era(1).player(ExecutionContextTestData.PLAYER_1));

        assertThat(IntStream.range(0, 4).mapToObj(i -> otherPurpose.nextLong())).isNotEqualTo(deal);
        assertThat(IntStream.range(0, 4).mapToObj(i -> otherSeat.nextLong())).isNotEqualTo(deal);
    }

    @Test
    @DisplayName("drawing in a different order across purposes does not change any purpose's sequence")
    void generator_interleavingOfPurposes_doesNotMatter() {
        var entropy = entropyFor("42");
        var timeout = entropy.generator(EntropyPurpose.HAND_TIMEOUT_SELECTION, SEAT_0_ERA_1);
        var deal = entropy.generator(EntropyPurpose.CARD_DEAL, SEAT_0_ERA_1);

        var interleaved = new long[] {deal.nextLong(), timeout.nextLong(), deal.nextLong(), timeout.nextLong()};

        var cleanEntropy = new SeededExecutionEntropy(new PinnedExecutionContext(executions), new InMemoryDecisions());
        var cleanDeal = cleanEntropy.generator(EntropyPurpose.CARD_DEAL, SEAT_0_ERA_1);
        var cleanTimeout = cleanEntropy.generator(EntropyPurpose.HAND_TIMEOUT_SELECTION, SEAT_0_ERA_1);
        assertThat(new long[] {
                    cleanDeal.nextLong(), cleanDeal.nextLong(), cleanTimeout.nextLong(), cleanTimeout.nextLong()
                })
                .containsExactly(interleaved[0], interleaved[2], interleaved[1], interleaved[3]);
    }

    @Test
    @DisplayName("bounded draws are repeatable and stay inside their bound")
    void generator_boundedDraws_areRepeatableAndInRange() {
        var entropy = entropyFor("42");
        var first = entropy.generator(EntropyPurpose.CARD_DEAL, SEAT_0_ERA_1);
        var firstDraws = IntStream.range(0, 50).map(i -> first.nextInt(7)).toArray();
        var second = entropy.generator(EntropyPurpose.CARD_DEAL, SEAT_0_ERA_1);
        var secondDraws = IntStream.range(0, 50).map(i -> second.nextInt(7)).toArray();

        assertThat(secondDraws).containsExactly(firstDraws);
        assertThat(IntStream.of(firstDraws)).allMatch(value -> value >= 0 && value < 7);
    }

    @Test
    @DisplayName("an identity is stable across repetitions and distinct per kind and coordinate")
    void identity_isStablePerKindAndCoordinate() {
        var entropy = entropyFor("42");

        var card = entropy.identity(IdentityKind.CARD_INSTANCE, SEAT_0_ERA_1.slot(0));

        assertThat(entropy.identity(IdentityKind.CARD_INSTANCE, SEAT_0_ERA_1.slot(0)))
                .isEqualTo(card);
        assertThat(entropy.identity(IdentityKind.CARD_INSTANCE, SEAT_0_ERA_1.slot(1)))
                .isNotEqualTo(card);
        assertThat(entropy.identity(IdentityKind.DECOY_CARD_INSTANCE, SEAT_0_ERA_1.slot(0)))
                .isNotEqualTo(card);
    }

    @Test
    @DisplayName("without a configured execution no choice or identity can be produced")
    void generator_withoutConfiguredExecution_isRejected() {
        given(executions.find()).willReturn(Optional.empty());
        var entropy = new SeededExecutionEntropy(new PinnedExecutionContext(executions), decisions);

        assertThatThrownBy(() -> entropy.generator(EntropyPurpose.CARD_DEAL, SEAT_0_ERA_1))
                .isInstanceOf(ExecutionNotConfiguredException.class);
        assertThatThrownBy(() -> entropy.identity(IdentityKind.GAME, EntropyCoordinate.none()))
                .isInstanceOf(ExecutionNotConfiguredException.class);
    }

    private static java.util.List<Long> draw(SeededExecutionEntropy entropy, int count) {
        var generator = entropy.generator(EntropyPurpose.CARD_DEAL, SEAT_0_ERA_1);
        return IntStream.range(0, count).mapToObj(i -> generator.nextLong()).toList();
    }

    private static final class InMemoryDecisions implements EntropyDecisionRepository {

        private final Map<String, Long> words = new HashMap<>();

        @Override
        public Optional<Long> find(String purpose, String coordinate, int drawIndex) {
            return Optional.ofNullable(words.get(key(purpose, coordinate, drawIndex)));
        }

        @Override
        public void save(String purpose, String coordinate, int drawIndex, long word) {
            words.putIfAbsent(key(purpose, coordinate, drawIndex), word);
        }

        int size() {
            return words.size();
        }

        private static String key(String purpose, String coordinate, int drawIndex) {
            return purpose + "|" + coordinate + "|" + drawIndex;
        }
    }
}
