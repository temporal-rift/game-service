package io.github.temporalrift.game.session.domain.game;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The catalog card a drawn event's per-game {@code eventId} originated from, and the exact per-game
 * {@code outcomeId}s minted for it, in the catalog definition's own outcome order. A carried-over event must
 * republish under these same outcome IDs — timeline-service never re-drafts a carried-over event's aggregate, so
 * its outcome IDs never change after the first draft.
 */
public record DrawnFutureEvent(UUID cardId, List<UUID> outcomeIds) {

    public DrawnFutureEvent {
        Objects.requireNonNull(cardId, "cardId must not be null");
        outcomeIds = List.copyOf(Objects.requireNonNull(outcomeIds, "outcomeIds must not be null"));
    }
}
