package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

public record SpecialActionPlayed(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID playerId,
        Faction faction,
        SpecialAction specialAction,
        UUID targetEventId,
        UUID targetOutcomeId,
        UUID targetPlayerId) {}
