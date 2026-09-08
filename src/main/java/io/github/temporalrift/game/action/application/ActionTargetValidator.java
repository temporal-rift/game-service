package io.github.temporalrift.game.action.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        validate(gameId, eraNumber, targetEventId, null, outcomeIds);
    }

    /**
     * Validates every scalar and list event coordinate from one current-era definition lookup and
     * returns the complete set of known event ids for card-specific domain validation.
     */
    public Set<UUID> validateCardTargets(
            UUID gameId, int eraNumber, UUID targetEventId, List<UUID> targetEventIds, UUID... outcomeIds) {
        return validate(gameId, eraNumber, targetEventId, targetEventIds, outcomeIds);
    }

    private Set<UUID> validate(
            UUID gameId, int eraNumber, UUID targetEventId, List<UUID> targetEventIds, UUID... outcomeIds) {
        var suppliedOutcomeIds = outcomeIds == null ? new UUID[0] : outcomeIds;
        var suppliedTargetEventIds = targetEventIds == null ? List.<UUID>of() : targetEventIds;
        if (targetEventId == null
                && suppliedTargetEventIds.isEmpty()
                && Arrays.stream(suppliedOutcomeIds).allMatch(Objects::isNull)) {
            return Set.of();
        }

        var definitions = futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, eraNumber);
        var knownEventIds = definitions.stream()
                .map(FutureEventDefinitionPort.EventDefinition::eventId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        suppliedTargetEventIds.forEach(eventId -> {
            if (!knownEventIds.contains(eventId)) {
                throw new UnknownActionTargetException(eventId);
            }
        });

        if (targetEventId == null) {
            return Set.copyOf(knownEventIds);
        }

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
        return Set.copyOf(knownEventIds);
    }
}
