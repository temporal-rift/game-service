package io.github.temporalrift.game.action.domain.specialactionerausage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SealGameUsage")
class SealGameUsageTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Test
    @DisplayName("claim — accepts uses up to the configured maximum")
    void claim_acceptsUpToMaximum() {
        var usage = new SealGameUsage(UUID.randomUUID(), GAME_ID, PLAYER_ID);

        usage.claim(2);
        usage.claim(2);

        assertThat(usage.acceptedUses()).isEqualTo(2);
        assertThat(usage.remainingUses(2)).isZero();
    }

    @Test
    @DisplayName("claim — beyond the maximum throws without changing state")
    void claim_beyondMaximumThrowsWithoutChangingState() {
        var usage = SealGameUsage.reconstitute(UUID.randomUUID(), GAME_ID, PLAYER_ID, 2);

        assertThatThrownBy(() -> usage.claim(2)).isInstanceOf(SealGameBudgetExhaustedException.class);
        assertThat(usage.acceptedUses()).isEqualTo(2);
    }

    @Test
    @DisplayName("remainingUses — reflects accepted uses against the configured maximum")
    void remainingUses_reflectsAcceptedUses() {
        var usage = SealGameUsage.reconstitute(UUID.randomUUID(), GAME_ID, PLAYER_ID, 1);

        assertThat(usage.remainingUses(2)).isEqualTo(1);
        assertThat(usage.remainingUses(1)).isZero();
    }
}
