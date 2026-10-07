package io.github.temporalrift.game.shared.infrastructure.adapter.out.entropy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;

class OrdinaryExecutionInputsTest {

    private final SecureExecutionEntropy entropy = new SecureExecutionEntropy();

    @Test
    @DisplayName("an ordinary deployment mints a fresh identity every time, whatever the coordinate")
    void identity_isUnpredictable() {
        var coordinate = EntropyCoordinate.none().era(1).player(UUID.randomUUID());

        assertThat(entropy.identity(IdentityKind.CARD_INSTANCE, coordinate))
                .isNotEqualTo(entropy.identity(IdentityKind.CARD_INSTANCE, coordinate));
    }

    @Test
    @DisplayName("an ordinary deployment shares one secure generator across purposes")
    void generator_isSharedAndSecure() {
        var first = entropy.generator(EntropyPurpose.CARD_DEAL, EntropyCoordinate.none());
        var second = entropy.generator(EntropyPurpose.EVENT_DECK_SHUFFLE, EntropyCoordinate.none());

        assertThat(first).isSameAs(second).isInstanceOf(java.security.SecureRandom.class);
    }

    @Test
    @DisplayName("an ordinary deployment has no pre-agreed seating")
    void seatingPlan_isEmpty() {
        assertThat(new OrdinarySeatingPlan().configuredSeats()).isEmpty();
    }
}
