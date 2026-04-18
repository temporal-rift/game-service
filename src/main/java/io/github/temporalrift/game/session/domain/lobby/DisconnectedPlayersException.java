package io.github.temporalrift.game.session.domain.lobby;

import java.util.List;
import java.util.UUID;

public class DisconnectedPlayersException extends RuntimeException {

    private final List<UUID> disconnectedPlayerIds;

    public DisconnectedPlayersException(List<UUID> disconnectedPlayerIds) {
        super("Cannot start game: " + disconnectedPlayerIds.size() + " player(s) are disconnected");
        this.disconnectedPlayerIds = List.copyOf(disconnectedPlayerIds);
    }

    public List<UUID> disconnectedPlayerIds() {
        return disconnectedPlayerIds;
    }
}
