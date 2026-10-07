package io.github.temporalrift.game.shared.infrastructure.adapter.out.entropy;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "false", matchIfMissing = true)
class SecureExecutionEntropy implements ExecutionEntropy {

    private final SecureRandom random = new SecureRandom();

    @Override
    public RandomGenerator generator(EntropyPurpose purpose, EntropyCoordinate coordinate) {
        return random;
    }

    @Override
    public UUID identity(IdentityKind kind, EntropyCoordinate coordinate) {
        return UUID.randomUUID();
    }
}
