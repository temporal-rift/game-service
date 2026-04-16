package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class LobbyPlayerPk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "lobby_id")
    private UUID lobbyId;

    @Column(name = "player_id")
    private UUID playerId;

    protected LobbyPlayerPk() {}

    LobbyPlayerPk(UUID lobbyId, UUID playerId) {
        this.lobbyId = lobbyId;
        this.playerId = playerId;
    }

    UUID getLobbyId() {
        return lobbyId;
    }

    UUID getPlayerId() {
        return playerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LobbyPlayerPk that)) {
            return false;
        }
        return Objects.equals(lobbyId, that.lobbyId) && Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lobbyId, playerId);
    }
}
