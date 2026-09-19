package io.github.temporalrift.game.action.domain.declarationphase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;

class DeclarationPhaseTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void assertOpen_acceptsBeforeExpiry() {
        var phase = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, NOW.plusSeconds(30));

        phase.assertOpen(NOW);

        assertThat(phase.status()).isEqualTo(DeclarationPhaseStatus.OPEN);
    }

    @Test
    void assertOpen_rejectsAtOrAfterExpiry() {
        var gameId = UUID.randomUUID();
        var phase = new DeclarationPhase(UUID.randomUUID(), gameId, 1, NOW);

        assertThatThrownBy(() -> phase.assertOpen(NOW)).isInstanceOf(DeclarationWindowClosedException.class);
    }

    @Test
    void assertOpen_rejectsClosedPhase() {
        var gameId = UUID.randomUUID();
        var phase = new DeclarationPhase(UUID.randomUUID(), gameId, 1, NOW.plusSeconds(30));
        phase.closeIfOpen(NOW.plusSeconds(31));

        assertThatThrownBy(() -> phase.assertOpen(NOW)).isInstanceOf(DeclarationWindowClosedException.class);
    }

    @Test
    void closeIfOpen_closesOnceAtExpiry() {
        var phase = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, NOW.plusSeconds(30));

        assertThat(phase.closeIfOpen(NOW.plusSeconds(30))).isTrue();
        assertThat(phase.status()).isEqualTo(DeclarationPhaseStatus.CLOSED);
        assertThat(phase.closeIfOpen(NOW.plusSeconds(31))).isFalse();
    }

    @Test
    void closeIfOpen_ignoresEarlyClose() {
        var phase = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, NOW.plusSeconds(30));

        assertThat(phase.closeIfOpen(NOW)).isFalse();
        assertThat(phase.status()).isEqualTo(DeclarationPhaseStatus.OPEN);
    }
}
