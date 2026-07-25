package io.github.temporalrift.game.shared;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the session module reveals every player's faction once the game is over; the
 * scoring module projects from it to make faction names visible in score reads. Lives in
 * {@code game.shared} - the neutral shared kernel - so referencing it never creates a Spring Modulith
 * module cycle. See {@link EventsDrawn} for the rationale.
 */
public record FactionRevealed(UUID gameId, List<PlayerFactionResult> reveals) {

    public record PlayerFactionResult(UUID playerId, String faction) {}
}
