package io.github.temporalrift.game.session;

import java.util.List;
import java.util.UUID;

/**
 * Public cross-module event published by the session module. Lives in session's top-level package
 * on purpose, following the same convention as {@link io.github.temporalrift.game.action.StartActionRoundRequested}
 * so other modules may reference it without reaching into session's internal {@code domain.event} package.
 */
public record EventsDrawn(UUID gameId, int eraNumber, List<FutureEvent> events) {

    public record FutureEvent(UUID eventId, String title, List<Outcome> outcomes, boolean isCascaded) {}

    public record Outcome(UUID outcomeId, String description, int initialProbability) {}
}
