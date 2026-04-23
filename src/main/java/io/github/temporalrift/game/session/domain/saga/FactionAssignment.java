package io.github.temporalrift.game.session.domain.saga;

import java.util.UUID;

import io.github.temporalrift.events.shared.Faction;

public record FactionAssignment(UUID playerId, Faction faction) {}
