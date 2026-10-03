package io.github.temporalrift.game.action.domain.declarationphase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;

class DeclarationPhaseTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void decisions_closeOnlyWhenEveryOfferedPlayerHasDecided() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var phase = new DeclarationPhase(
                UUID.randomUUID(), UUID.randomUUID(), 1, NOW.plusSeconds(120), List.of(first, second));
        assertThat(phase.decide(first, DeclarationDecision.DECLARED, NOW)).isFalse();
        assertThat(phase.status()).isEqualTo(DeclarationPhaseStatus.OPEN);
        assertThat(phase.decide(second, DeclarationDecision.DECLINED, NOW)).isTrue();
        assertThat(phase.status()).isEqualTo(DeclarationPhaseStatus.CLOSED);
        assertThat(phase.hasDeclined(second)).isTrue();
        assertThat(phase.closeIfComplete()).isFalse();
        assertThatThrownBy(() -> phase.decide(first, DeclarationDecision.DECLINED, NOW))
                .isInstanceOf(DeclarationAlreadyDecidedException.class);
    }

    @Test
    void emptyOpportunitySet_closesImmediately() {
        var phase = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, NOW.plusSeconds(120), List.of());
        assertThat(phase.closeIfComplete()).isTrue();
        assertThat(phase.status()).isEqualTo(DeclarationPhaseStatus.CLOSED);
    }

    @Test
    void undecidedOpportunity_expiresAndRejectsLateDecision() {
        var player = UUID.randomUUID();
        var phase = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, NOW, List.of(player));
        assertThatThrownBy(() -> phase.decide(player, DeclarationDecision.DECLARED, NOW))
                .isInstanceOf(DeclarationWindowClosedException.class);
        assertThat(phase.decisions().get(player)).isEqualTo(DeclarationDecision.PENDING);
        assertThat(phase.closeIfOpen(NOW)).isTrue();
    }

    @Test
    void unofferedPlayer_cannotDecide() {
        var phase = new DeclarationPhase(
                UUID.randomUUID(), UUID.randomUUID(), 1, NOW.plusSeconds(120), List.of(UUID.randomUUID()));
        assertThatThrownBy(() -> phase.decide(UUID.randomUUID(), DeclarationDecision.DECLINED, NOW))
                .isInstanceOf(DeclarationWindowClosedException.class);
    }

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
