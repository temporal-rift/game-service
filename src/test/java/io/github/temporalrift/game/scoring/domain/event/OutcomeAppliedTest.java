package io.github.temporalrift.game.scoring.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutcomeAppliedTest {

    private static final UUID ERASED = UUID.randomUUID();

    @Test
    @DisplayName("a listed erasure is credited and an unlisted one is not")
    void listedErasureIsCredited() {
        var outcome = outcome(List.of(ERASED));

        assertThat(outcome.creditsErasureOf(ERASED)).isTrue();
        assertThat(outcome.creditsErasureOf(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("an empty list credits no erasure")
    void emptyListCreditsNothing() {
        assertThat(outcome(List.of()).creditsErasureOf(ERASED)).isFalse();
    }

    @Test
    @DisplayName("an absent list, from a producer that predates it, credits every erasure as before")
    void absentListKeepsTheEarlierRule() {
        var outcome = outcome(null);

        assertThat(outcome.consequentialErasures()).isNull();
        assertThat(outcome.creditsErasureOf(ERASED)).isTrue();
    }

    @Test
    @DisplayName("the list is copied, so a later change to the source does not alter the outcome")
    void listIsCopied() {
        var source = new ArrayList<UUID>();
        var outcome = outcome(source);

        source.add(ERASED);

        assertThat(outcome.consequentialErasures()).isEmpty();
    }

    private static OutcomeApplied outcome(List<UUID> consequentialErasures) {
        return new OutcomeApplied(
                UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), List.of(), consequentialErasures);
    }
}
