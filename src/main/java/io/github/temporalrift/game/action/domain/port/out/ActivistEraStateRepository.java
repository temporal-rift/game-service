package io.github.temporalrift.game.action.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;

public interface ActivistEraStateRepository {

    ActivistEraState save(ActivistEraState state);

    Optional<ActivistEraState> findByGameIdAndEraNumberAndActivistPlayerId(
            UUID gameId, int eraNumber, UUID activistPlayerId);

    List<ActivistEraState> findDeclaredByGameIdAndEraNumber(UUID gameId, int eraNumber);

    List<ActivistEraState> findExposedByGameIdAndEraNumber(UUID gameId, int eraNumber);
}
