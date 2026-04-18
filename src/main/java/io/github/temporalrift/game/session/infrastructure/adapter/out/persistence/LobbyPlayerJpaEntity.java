package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "lobby_player")
class LobbyPlayerJpaEntity {

    @EmbeddedId
    private LobbyPlayerPk id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("lobbyId")
    @JoinColumn(name = "lobby_id", nullable = false)
    private LobbyJpaEntity lobby;

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @Column(name = "faction")
    private String faction;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "connected", nullable = false)
    private boolean connected;

    protected LobbyPlayerJpaEntity() {}

    LobbyPlayerPk getId() {
        return id;
    }

    void setId(LobbyPlayerPk id) {
        this.id = id;
    }

    LobbyJpaEntity getLobby() {
        return lobby;
    }

    void setLobby(LobbyJpaEntity lobby) {
        this.lobby = lobby;
    }

    String getPlayerName() {
        return playerName;
    }

    void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    String getFaction() {
        return faction;
    }

    void setFaction(String faction) {
        this.faction = faction;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }

    void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    boolean isConnected() {
        return connected;
    }

    void setConnected(boolean connected) {
        this.connected = connected;
    }
}
