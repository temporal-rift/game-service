package io.github.temporalrift.game.action.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;

/** Decides Momentum eligibility: the player's immediately preceding era ended with a successful declaration. */
@Component
public class ActivistMomentumEligibility {

    private final ActivistEraStateRepository activistEraStateRepository;

    public ActivistMomentumEligibility(ActivistEraStateRepository activistEraStateRepository) {
        this.activistEraStateRepository = activistEraStateRepository;
    }

    public boolean isEligible(UUID gameId, int eraNumber, UUID playerId) {
        if (eraNumber == 1) {
            return false;
        }
        return activistEraStateRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(gameId, eraNumber - 1, playerId)
                .map(ActivistEraState::declarationSucceeded)
                .orElse(false);
    }
}
