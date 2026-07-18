package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

import io.github.temporalrift.game.shared.Faction;

public record PlayerFaction(UUID playerId, Faction faction) {}
