package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "lobby")
class LobbyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "host_player_id", nullable = false)
    private UUID hostPlayerId;

    @Column(name = "join_code", nullable = false)
    private String joinCode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "min_players", nullable = false)
    private int minPlayers;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @OneToMany(mappedBy = "lobby", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LobbyPlayerJpaEntity> players = new ArrayList<>();

    protected LobbyJpaEntity() {}

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getGameId() {
        return gameId;
    }

    void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    UUID getHostPlayerId() {
        return hostPlayerId;
    }

    void setHostPlayerId(UUID hostPlayerId) {
        this.hostPlayerId = hostPlayerId;
    }

    String getJoinCode() {
        return joinCode;
    }

    void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    List<LobbyPlayerJpaEntity> getPlayers() {
        return players;
    }

    void setPlayers(List<LobbyPlayerJpaEntity> players) {
        this.players.clear();
        this.players.addAll(players);
    }

    int getMinPlayers() {
        return minPlayers;
    }

    void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    int getMaxPlayers() {
        return maxPlayers;
    }

    void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
