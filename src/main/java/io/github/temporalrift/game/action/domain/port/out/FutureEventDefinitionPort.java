package io.github.temporalrift.game.action.domain.port.out;

import java.util.List;
import java.util.UUID;

public interface FutureEventDefinitionPort {

    record EventDefinition(UUID eventId, List<OutcomeDefinition> outcomes) {}

    record OutcomeDefinition(UUID outcomeId, int initialProbability) {}

    List<EventDefinition> findByGameIdAndEraNumber(UUID gameId, int eraNumber);
}
