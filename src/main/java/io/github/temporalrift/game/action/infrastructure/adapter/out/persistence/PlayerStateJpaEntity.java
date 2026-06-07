package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_state_hand_card", joinColumns = @JoinColumn(name = "player_state_id"))
    @OrderColumn(name = "card_position")
    private List<PlayerHandCardValue> hand;

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

    List<PlayerHandCardValue> getHand() {
        return hand;
    }

    void setHand(List<PlayerHandCardValue> hand) {
        this.hand = hand;
    }
}
