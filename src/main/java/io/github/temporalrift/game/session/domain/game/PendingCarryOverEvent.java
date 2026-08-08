package io.github.temporalrift.game.session.domain.game;

import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.shared.CarryOverState;

/** A terminal event retained in reveal order for the following era. */
public record PendingCarryOverEvent(UUID eventId, CarryOverState carryOverState) {

    public PendingCarryOverEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(carryOverState, "carryOverState must not be null");
        if (carryOverState == CarryOverState.FRESH) {
            throw new IllegalArgumentException("a pending carry-over event must not be fresh");
        }
    }
}
