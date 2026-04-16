package io.github.temporalrift.game.session.domain.lobby;

import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import io.github.temporalrift.events.shared.Faction;

public record LobbyPlayer(UUID playerId, String playerName, Faction faction) {

    public LobbyPlayer {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        if (StringUtils.isBlank(playerName)) {
            throw new IllegalArgumentException("playerName cannot be null or blank");
        }
    }
}
