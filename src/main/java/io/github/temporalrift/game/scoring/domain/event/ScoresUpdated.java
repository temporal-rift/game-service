package io.github.temporalrift.game.scoring.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.Faction;

public record ScoresUpdated(UUID gameId, int eraNumber, List<ScoreUpdate> updates) {

    public record ScoreUpdate(UUID playerId, Faction faction, int pointsDelta, String reason, int newTotal) {}
}
