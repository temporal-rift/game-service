package io.github.temporalrift.game.shared;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the scoring module recalculates scores for an era; the session module's
 * era-advancement saga reacts to it. Lives in {@code game.shared} - the neutral shared kernel - so
 * referencing it never creates a Spring Modulith module cycle. See {@link EventsDrawn} for the rationale.
 */
public record ScoresUpdated(UUID gameId, int eraNumber, List<ScoreUpdate> updates) {

    public record ScoreUpdate(UUID playerId, Faction faction, int pointsDelta, String reason, int newTotal) {}
}
