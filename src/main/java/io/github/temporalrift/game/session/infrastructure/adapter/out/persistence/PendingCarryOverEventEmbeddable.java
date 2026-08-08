package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.shared.CarryOverState;

@Embeddable
class PendingCarryOverEventEmbeddable {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "carry_over_state", nullable = false)
    private String carryOverState;

    protected PendingCarryOverEventEmbeddable() {}

    private PendingCarryOverEventEmbeddable(UUID eventId, String carryOverState) {
        this.eventId = eventId;
        this.carryOverState = carryOverState;
    }

    static PendingCarryOverEventEmbeddable fromDomain(PendingCarryOverEvent event) {
        return new PendingCarryOverEventEmbeddable(
                event.eventId(), event.carryOverState().name());
    }

    PendingCarryOverEvent toDomain() {
        return new PendingCarryOverEvent(eventId, CarryOverState.valueOf(carryOverState));
    }
}
