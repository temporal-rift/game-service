package io.github.temporalrift.game.action.domain.handselection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.HandSelected;

class HandSelectionTest {
    @Test
    void playerSelection_keepsExactlyTheRequestedPendingCards() {
        var selection = selection(Instant.parse("2026-08-14T00:00:10Z"));
        var kept = selection.dealtCards().stream()
                .limit(5)
                .map(HandDealt.CardInstance::cardInstanceId)
                .collect(java.util.stream.Collectors.toSet());
        var resolved = selection.select(kept, Instant.parse("2026-08-14T00:00:00Z"));
        assertThat(resolved.selectedCards())
                .extracting(HandDealt.CardInstance::cardInstanceId)
                .containsExactlyInAnyOrderElementsOf(kept);
        assertThat(resolved.selectionOrigin()).isEqualTo(HandSelected.SelectionOrigin.PLAYER);
    }

    @Test
    void expiry_canReachEveryUniformFiveCardSubset() {
        var selection = selection(Instant.EPOCH);
        var subsets = new java.util.HashSet<Set<UUID>>();
        for (var firstDiscardIndex = 0; firstDiscardIndex < 7; firstDiscardIndex++) {
            for (var secondDiscardRoll = 0; secondDiscardRoll < 6; secondDiscardRoll++) {
                var resolved = selection.selectRandomOnExpiry(
                        Instant.EPOCH, new SequenceRandomGenerator(firstDiscardIndex, secondDiscardRoll));
                subsets.add(resolved.selectedCards().stream()
                        .map(HandDealt.CardInstance::cardInstanceId)
                        .collect(java.util.stream.Collectors.toSet()));
                assertThat(resolved.selectionOrigin()).isEqualTo(HandSelected.SelectionOrigin.TIMEOUT_RANDOM);
            }
        }
        assertThat(subsets).hasSize(21);
    }

    @Test
    void invalidPlayerSelection_isRejected() {
        var selection = selection(Instant.parse("2026-08-14T00:00:10Z"));
        assertThatThrownBy(() -> selection.select(Set.of(UUID.randomUUID()), Instant.EPOCH))
                .isInstanceOf(InvalidHandSelectionException.class);
    }

    @Test
    void expiredOrAlreadyResolvedSelection_cannotResolveTwice() {
        var selection = selection(Instant.EPOCH);
        assertThatThrownBy(() -> selection.select(Set.of(UUID.randomUUID()), Instant.EPOCH))
                .isInstanceOf(HandSelectionNotOpenException.class);
        var resolved = selection.selectRandomOnExpiry(Instant.EPOCH, new SequenceRandomGenerator(0, 0));
        assertThat(resolved.selectRandomOnExpiry(Instant.EPOCH, new SequenceRandomGenerator(1, 1)))
                .isSameAs(resolved);
    }

    private static HandSelection selection(Instant expiresAt) {
        var cards = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(slot -> new HandDealt.CardInstance(
                        UUID.randomUUID(), CardType.PUSH, io.github.temporalrift.game.shared.CardGrade.I, slot))
                .toList();
        return HandSelection.open(new HandDealt(UUID.randomUUID(), 1, UUID.randomUUID(), expiresAt, cards));
    }

    private static final class SequenceRandomGenerator implements java.util.random.RandomGenerator {
        private final int[] values;
        private int index;

        private SequenceRandomGenerator(int... values) {
            this.values = values;
        }

        @Override
        public long nextLong() {
            return nextInt(Integer.MAX_VALUE);
        }

        @Override
        public int nextInt(int bound) {
            return values[index++] % bound;
        }
    }
}
