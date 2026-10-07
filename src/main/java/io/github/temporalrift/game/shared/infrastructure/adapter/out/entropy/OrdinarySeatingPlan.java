package io.github.temporalrift.game.shared.infrastructure.adapter.out.entropy;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.SeatingPlan;

@Component
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "false", matchIfMissing = true)
class OrdinarySeatingPlan implements SeatingPlan {

    @Override
    public Optional<List<SeatAssignment>> configuredSeats() {
        return Optional.empty();
    }
}
