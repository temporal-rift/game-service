package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.github.temporalrift.game.session.domain.game.DrawnFutureEvent;

/**
 * One entry of a game's drawn-event map: a per-game {@code eventId} and the {@link DrawnFutureEvent} it was drawn
 * with. Outcome IDs are always exactly 3 (GDD §4.1), so a simple delimited column avoids a nested
 * {@code @ElementCollection} inside this embeddable, which JPA does not support cleanly.
 */
@Embeddable
class DrawnEventEmbeddable {

    private static final String OUTCOME_ID_DELIMITER = ",";

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "outcome_ids", nullable = false)
    private String outcomeIds;

    protected DrawnEventEmbeddable() {}

    private DrawnEventEmbeddable(UUID eventId, UUID cardId, String outcomeIds) {
        this.eventId = eventId;
        this.cardId = cardId;
        this.outcomeIds = outcomeIds;
    }

    static DrawnEventEmbeddable of(UUID eventId, DrawnFutureEvent drawnEvent) {
        return new DrawnEventEmbeddable(
                eventId,
                drawnEvent.cardId(),
                drawnEvent.outcomeIds().stream().map(UUID::toString).collect(Collectors.joining(OUTCOME_ID_DELIMITER)));
    }

    UUID eventId() {
        return eventId;
    }

    DrawnFutureEvent toDrawnFutureEvent() {
        return new DrawnFutureEvent(cardId, parseOutcomeIds());
    }

    private List<UUID> parseOutcomeIds() {
        return Stream.of(outcomeIds.split(OUTCOME_ID_DELIMITER))
                .map(UUID::fromString)
                .toList();
    }
}
