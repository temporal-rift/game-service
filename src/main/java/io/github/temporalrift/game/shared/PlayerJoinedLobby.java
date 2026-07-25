package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Cross-module event: a player joins a lobby; the scoring module projects the display name from it so
 * score reads can return {@code playerName}. Lives in {@code game.shared} - the neutral shared kernel -
 * so referencing it never creates a Spring Modulith module cycle. See {@link EventsDrawn} for the
 * rationale.
 *
 * <p>Carries the pre-assigned {@code gameId} (in addition to the {@code lobbyId} the Kafka payload
 * exposes) because in-process listeners receive the typed event directly, without the envelope headers
 * that would otherwise carry it. See developer-notes.md §4.
 */
public record PlayerJoinedLobby(UUID gameId, UUID lobbyId, UUID playerId, String playerName) {}
