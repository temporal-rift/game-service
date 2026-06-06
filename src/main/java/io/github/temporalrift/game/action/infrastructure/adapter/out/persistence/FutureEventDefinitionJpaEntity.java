package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

@Entity
@Table(name = "action_future_event_definition")
class FutureEventDefinitionJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "outcomes", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = OutcomeDefinitionListConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<FutureEventDefinitionPort.OutcomeDefinition> outcomes;

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

    List<FutureEventDefinitionPort.OutcomeDefinition> getOutcomes() {
        return outcomes;
    }

    void setOutcomes(List<FutureEventDefinitionPort.OutcomeDefinition> outcomes) {
        this.outcomes = outcomes;
    }
}
