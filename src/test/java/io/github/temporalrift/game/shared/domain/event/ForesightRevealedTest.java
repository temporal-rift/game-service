package io.github.temporalrift.game.shared.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ForesightRevealedTest {

    @Test
    void constructor_copiesPreviewEntries() {
        var entries = new java.util.ArrayList<>(List.of(
                new ForesightRevealed.RevealedEvent(UUID.randomUUID(), "Storm", List.of()),
                new ForesightRevealed.RevealedEvent(UUID.randomUUID(), "Calm", List.of())));

        var revealed = new ForesightRevealed(UUID.randomUUID(), 1, UUID.randomUUID(), 2, entries, null);

        entries.clear();
        assertThat(revealed.revealedEvents()).hasSize(2);
        assertThat(revealed.nextEraNumber()).isEqualTo(2);
    }

    @Test
    void constructor_rejectsNextEraNumberOutsideTheFollowingEra() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new ForesightRevealed(UUID.randomUUID(), 1, UUID.randomUUID(), 3, List.of(), "final-era"));
    }

    @Test
    void constructor_rejectsEmptyPreviewWithoutAReason() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ForesightRevealed(UUID.randomUUID(), 5, UUID.randomUUID(), 6, List.of(), null));
    }
}
