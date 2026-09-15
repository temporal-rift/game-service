package io.github.temporalrift.game.session.domain.saga;

import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.Faction;

public record FactionAssignment(UUID playerId, Faction faction) {}
