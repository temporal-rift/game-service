package io.github.temporalrift.game.action.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivistMomentumEligibility")
class ActivistMomentumEligibilityTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    ActivistEraStateRepository activistEraStateRepository;

    ActivistMomentumEligibility eligibility;

    @BeforeEach
    void setUp() {
        eligibility = new ActivistMomentumEligibility(activistEraStateRepository);
    }

    @Test
    @DisplayName("isEligible — era 1 — never eligible, without a lookup")
    void firstEraIsNeverEligible() {
        assertThat(eligibility.isEligible(GAME_ID, 1, PLAYER_ID)).isFalse();
        then(activistEraStateRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("isEligible — preceding era succeeded — eligible")
    void precedingSuccessIsEligible() {
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, 2, PLAYER_ID))
                .willReturn(Optional.of(resolvedEra(2, true)));

        assertThat(eligibility.isEligible(GAME_ID, 3, PLAYER_ID)).isTrue();
    }

    @Test
    @DisplayName("isEligible — preceding era failed or absent — not eligible")
    void precedingFailureOrAbsenceIsNotEligible() {
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, 1, PLAYER_ID))
                .willReturn(Optional.of(resolvedEra(1, false)));
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, 2, PLAYER_ID))
                .willReturn(Optional.empty());

        assertThat(eligibility.isEligible(GAME_ID, 2, PLAYER_ID)).isFalse();
        assertThat(eligibility.isEligible(GAME_ID, 3, PLAYER_ID)).isFalse();
    }

    private static ActivistEraState resolvedEra(int eraNumber, boolean succeeded) {
        var state = new ActivistEraState(UUID.randomUUID(), GAME_ID, eraNumber, PLAYER_ID, false);
        state.declare(ActivistDeclarationMode.RALLY, UUID.randomUUID(), UUID.randomUUID());
        state.recordResolution(succeeded);
        return state;
    }
}
