package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;

@Entity
@Table(name = "player_state")
class PlayerStateJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "faction")
    private String faction;

    @Column(name = "jammed", nullable = false)
    private boolean jammed;

    @Column(name = "hand", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = PlayerHandConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<PlayerState.CardInstance> hand;

    protected PlayerStateJpaEntity() {}

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

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    String getFaction() {
        return faction;
    }

    void setFaction(String faction) {
        this.faction = faction;
    }

    boolean isJammed() {
        return jammed;
    }

    void setJammed(boolean jammed) {
        this.jammed = jammed;
    }

    List<PlayerState.CardInstance> getHand() {
        return hand;
    }

    void setHand(List<PlayerState.CardInstance> hand) {
        this.hand = hand;
    }
}
