package io.github.temporalrift.game.shared;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the session module draws an era's future events; the action and scoring modules
 * project from it. Lives in {@code game.shared} - the neutral shared kernel that every module may depend
 * on but which depends on none of them - so referencing it never creates a Spring Modulith module cycle,
 * exactly as the shared enums ({@link Faction} etc.) already do.
 */
public record EventsDrawn(UUID gameId, int eraNumber, List<FutureEvent> events) {

    public record FutureEvent(UUID eventId, String title, List<Outcome> outcomes, boolean isCascaded) {}

    public record Outcome(UUID outcomeId, String description, int initialProbability) {}
}
