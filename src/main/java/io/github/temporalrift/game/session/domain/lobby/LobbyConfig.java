package io.github.temporalrift.game.session.domain.lobby;

import java.time.Clock;
import java.util.Objects;

public record LobbyConfig(String joinCode, int minPlayers, int maxPlayers, Clock clock) {

    public LobbyConfig {
        Objects.requireNonNull(joinCode, "joinCode must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
    }
}
