package com.temporalrift.game.session.domain.lobby;

import com.temporalrift.events.shared.Faction;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.UUID;

public record LobbyPlayer(UUID playerId, String playerName, boolean isHost, Faction faction) {

    public LobbyPlayer {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        if (StringUtils.isBlank(playerName)) {
            throw new IllegalArgumentException("playerName cannot be null or blank");
        }
    }
}
