package io.github.temporalrift.game.shared.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.Faction;

/** Pre-agreed seat and faction assignments of an isolated execution; empty in an ordinary deployment. */
public interface SeatingPlan {

    Optional<List<SeatAssignment>> configuredSeats();

    record SeatAssignment(int seatIndex, UUID playerId, Faction faction) {}
}
