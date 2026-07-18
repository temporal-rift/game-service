package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record EventsDrawn(UUID gameId, int eraNumber, List<FutureEvent> events) {

    public record FutureEvent(UUID eventId, String title, List<Outcome> outcomes, boolean isCascaded) {}

    public record Outcome(UUID outcomeId, String description, int initialProbability) {}
}
