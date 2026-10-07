package io.github.temporalrift.game.simulation.domain.execution;

import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.Faction;

public record SimulationSeat(int seatIndex, UUID playerId, Faction faction) {}
