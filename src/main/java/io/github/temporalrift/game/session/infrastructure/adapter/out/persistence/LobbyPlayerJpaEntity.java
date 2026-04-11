package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lobby_player")
class LobbyPlayerJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lobby_id", nullable = false)
    private LobbyJpaEntity lobby;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "faction")
    private String faction;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected LobbyPlayerJpaEntity() {}

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    LobbyJpaEntity getLobby() {
        return lobby;
    }

    void setLobby(LobbyJpaEntity lobby) {
        this.lobby = lobby;
    }

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    String getPlayerName() {
        return playerName;
    }

    void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    boolean isHost() {
        return isHost;
    }

    void setHost(boolean isHost) {
        this.isHost = isHost;
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
}
