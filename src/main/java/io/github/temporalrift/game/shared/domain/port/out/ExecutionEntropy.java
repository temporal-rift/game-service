package io.github.temporalrift.game.shared.domain.port.out;

import java.util.UUID;
import java.util.random.RandomGenerator;

import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;

/**
 * Source of every gameplay-affecting random choice and gameplay-significant identity. An isolated simulation
 * deployment answers deterministically per purpose and coordinate, so a redelivered or recovered step repeats its
 * original choices; ordinary deployments answer unpredictably.
 */
public interface ExecutionEntropy {

    /** A generator whose sequence is fixed by the purpose and coordinate when the deployment is deterministic. */
    RandomGenerator generator(EntropyPurpose purpose, EntropyCoordinate coordinate);

    UUID identity(IdentityKind kind, EntropyCoordinate coordinate);
}
