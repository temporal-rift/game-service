package io.github.temporalrift.game.action.application;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.actionround.UnknownActionTargetException;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

/**
 * Confirms that a submitted target event/outcome belongs to the game's current era before an
 * action round aggregate is asked to accept it, so stale or forged references never reach
 * resolution as a documented 422 rather than a downstream Kafka/outbox failure.
 */
@Component
public class ActionTargetValidator {

    private final FutureEventDefinitionPort futureEventDefinitionPort;

    public ActionTargetValidator(FutureEventDefinitionPort futureEventDefinitionPort) {
        this.futureEventDefinitionPort = futureEventDefinitionPort;
    }

    public void validate(UUID gameId, int eraNumber, UUID targetEventId, UUID... outcomeIds) {
        var suppliedOutcomeIds = outcomeIds == null ? new UUID[0] : outcomeIds;
        if (targetEventId == null && Arrays.stream(suppliedOutcomeIds).allMatch(Objects::isNull)) {
            return;
        }

        var definitions = futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, eraNumber);
        var event = definitions.stream()
                .filter(definition -> definition.eventId().equals(targetEventId))
                .findFirst()
                .orElseThrow(() -> new UnknownActionTargetException(targetEventId));

        Arrays.stream(suppliedOutcomeIds).filter(Objects::nonNull).forEach(outcomeId -> {
            var known = event.outcomes().stream()
                    .anyMatch(outcome -> outcome.outcomeId().equals(outcomeId));
            if (!known) {
                throw new UnknownActionTargetException(outcomeId);
            }
        });
    }
}
