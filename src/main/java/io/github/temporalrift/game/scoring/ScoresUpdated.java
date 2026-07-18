package io.github.temporalrift.game.scoring;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.Faction;

/**
 * Public cross-module event published by the scoring module. Lives in scoring's top-level package
 * on purpose, following the same convention as {@link io.github.temporalrift.game.action.StartActionRoundRequested}
 * so other modules may reference it without reaching into scoring's internal {@code domain.event} package.
 */
public record ScoresUpdated(UUID gameId, int eraNumber, List<ScoreUpdate> updates) {

    public record ScoreUpdate(UUID playerId, Faction faction, int pointsDelta, String reason, int newTotal) {}
}
