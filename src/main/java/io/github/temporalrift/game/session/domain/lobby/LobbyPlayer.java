package io.github.temporalrift.game.session.domain.lobby;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import io.github.temporalrift.game.shared.domain.model.Faction;

public record LobbyPlayer(UUID playerId, String playerName, Faction faction, Instant joinedAt, boolean connected) {

    /** Longest display name the lobby accepts; mirrors the session contract bound. */
    public static final int MAX_PLAYER_NAME_LENGTH = 32;

    public LobbyPlayer {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        if (StringUtils.isBlank(playerName)) {
            throw new InvalidPlayerNameException("playerName cannot be null or blank");
        }
        if (playerName.length() > MAX_PLAYER_NAME_LENGTH) {
            throw new InvalidPlayerNameException("playerName cannot exceed " + MAX_PLAYER_NAME_LENGTH + " characters");
        }
    }

    public LobbyPlayer withFaction(Faction newFaction) {
        return new LobbyPlayer(playerId, playerName, newFaction, joinedAt, connected);
    }

    public LobbyPlayer withConnected(boolean newConnected) {
        return new LobbyPlayer(playerId, playerName, faction, joinedAt, newConnected);
    }
}
