package io.github.temporalrift.game.simulation.infrastructure.adapter.out.entropy;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.SeatingPlan;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class ConfiguredSeatingPlan implements SeatingPlan {

    private final PinnedExecutionContext pinned;

    ConfiguredSeatingPlan(PinnedExecutionContext pinned) {
        this.pinned = pinned;
    }

    @Override
    public Optional<List<SeatAssignment>> configuredSeats() {
        return Optional.of(pinned.require().seats().stream()
                .map(seat -> new SeatAssignment(seat.seatIndex(), seat.playerId(), seat.faction()))
                .toList());
    }
}
