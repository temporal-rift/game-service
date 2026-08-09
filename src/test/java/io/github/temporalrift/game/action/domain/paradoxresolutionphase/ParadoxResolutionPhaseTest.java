package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.CardType;

class ParadoxResolutionPhaseTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final int ERA = 2;
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void acceptsEachSupportedResolutionCard() {
        for (var cardType : Set.of(CardType.PUSH, CardType.SUPPRESS, CardType.STABILIZE, CardType.DETONATE)) {
            var phase = openPhase();

            assertThatCode(() -> phase.submit(PLAYER_ID, cardType, NOW)).doesNotThrowAnyException();
            assertThat(phase.submittedPlayerIds()).containsExactly(PLAYER_ID);
        }
    }

    @Test
    void rejectsCardsOutsideTheResolutionSet() {
        var phase = openPhase();

        assertThatExceptionOfType(CardNotEligibleForParadoxResolutionException.class)
                .isThrownBy(() -> phase.submit(PLAYER_ID, CardType.COLLIDE, NOW));
        assertThat(phase.submittedPlayerIds()).isEmpty();
    }

    @Test
    void rejectsASecondSubmissionFromTheSamePlayer() {
        var phase = openPhase();
        phase.submit(PLAYER_ID, CardType.STABILIZE, NOW);

        assertThatExceptionOfType(DuplicateParadoxResolutionSubmissionException.class)
                .isThrownBy(() -> phase.submit(PLAYER_ID, CardType.DETONATE, NOW));
    }

    @Test
    void rejectsSubmissionAtOrAfterExpiry() {
        var phase = openPhase();
        var expiredAt = NOW.plusSeconds(60);

        assertThatExceptionOfType(ParadoxResolutionPhaseNotOpenException.class)
                .isThrownBy(() -> phase.submit(PLAYER_ID, CardType.STABILIZE, expiredAt));
    }

    @Test
    void rejectsSubmissionAfterClose() {
        var phase = openPhase();
        phase.close();

        assertThatExceptionOfType(ParadoxResolutionPhaseNotOpenException.class)
                .isThrownBy(() -> phase.submit(PLAYER_ID, CardType.STABILIZE, NOW));
    }

    private ParadoxResolutionPhase openPhase() {
        return new ParadoxResolutionPhase(UUID.randomUUID(), GAME_ID, ERA, NOW.plusSeconds(60));
    }
}
