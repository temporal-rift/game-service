package io.github.temporalrift.game.shared.domain.port.out;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;

/** Test entropy: every purpose shares one generator and identities are random, like an ordinary deployment. */
public final class StubExecutionEntropy implements ExecutionEntropy {

    private final RandomGenerator random;

    private StubExecutionEntropy(RandomGenerator random) {
        this.random = random;
    }

    public static StubExecutionEntropy unpredictable() {
        return new StubExecutionEntropy(new SecureRandom());
    }

    public static StubExecutionEntropy seeded(long seed) {
        return new StubExecutionEntropy(new Random(seed));
    }

    public static StubExecutionEntropy using(RandomGenerator random) {
        return new StubExecutionEntropy(random);
    }

    @Override
    public RandomGenerator generator(EntropyPurpose purpose, EntropyCoordinate coordinate) {
        return random;
    }

    @Override
    public UUID identity(IdentityKind kind, EntropyCoordinate coordinate) {
        return UUID.randomUUID();
    }
}
