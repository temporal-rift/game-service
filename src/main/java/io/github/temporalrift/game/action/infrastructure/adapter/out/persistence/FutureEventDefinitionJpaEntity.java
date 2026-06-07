package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "action_future_event_definition")
class FutureEventDefinitionJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "action_future_event_outcome",
            joinColumns = @JoinColumn(name = "future_event_definition_id"))
    @OrderColumn(name = "outcome_position")
    private List<FutureEventOutcomeValue> outcomes;

    protected FutureEventDefinitionJpaEntity() {}

    UUID getEventId() {
        return eventId;
    }

    void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    List<FutureEventOutcomeValue> getOutcomes() {
        return outcomes;
    }

    void setOutcomes(List<FutureEventOutcomeValue> outcomes) {
        this.outcomes = outcomes;
    }
}
