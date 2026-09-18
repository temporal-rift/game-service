package io.github.temporalrift.game.shared.domain.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-module event: the session module reveals the next era's freshly-drawn future events to the
 * Prophet who played Foresight. Private per-player reveal addressed to the foretelling viewer alone;
 * {@code playerId} identifies that viewer and the payload never identifies any other player. Lives in
 * {@code game.shared} for the same reason as {@link EventsDrawn}.
 */
public record ForesightRevealed(
        UUID gameId,
        int eraNumber,
        UUID playerId,
        int nextEraNumber,
        List<RevealedEvent> revealedEvents,
        String emptyReason) {

    public ForesightRevealed {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(revealedEvents, "revealedEvents must not be null");
        if (nextEraNumber != eraNumber + 1) {
            throw new IllegalArgumentException("nextEraNumber must be eraNumber + 1");
        }
        if (revealedEvents.isEmpty() && emptyReason == null) {
            throw new IllegalArgumentException("emptyReason must be present when no events are revealed");
        }
        revealedEvents = List.copyOf(revealedEvents);
    }

    public record RevealedEvent(UUID catalogEventId, String title, List<RevealedOutcome> outcomes) {

        public RevealedEvent {
            Objects.requireNonNull(catalogEventId, "catalogEventId must not be null");
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(outcomes, "outcomes must not be null");
            outcomes = List.copyOf(outcomes);
        }
    }

    public record RevealedOutcome(UUID catalogOutcomeId, String description) {

        public RevealedOutcome {
            Objects.requireNonNull(catalogOutcomeId, "catalogOutcomeId must not be null");
            Objects.requireNonNull(description, "description must not be null");
        }
    }
}
