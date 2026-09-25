package io.github.temporalrift.game.shared.domain.event;

import java.util.List;
import java.util.UUID;

/** The game's roster is fixed here: {@code players} in seat order, each with the name chosen in the lobby. */
public record GameStarted(UUID gameId, UUID lobbyId, List<Player> players, int totalFactions, int deckSize) {

    public GameStarted {
        players = List.copyOf(players);
    }

    public record Player(UUID playerId, String playerName) {}
}
