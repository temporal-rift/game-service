package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_state")
class PlayerStateJpaEntity extends GamePlayerScopedJpaEntity {

    @Column(name = "faction")
    private String faction;

    @Column(name = "jammed", nullable = false)
    private boolean jammed;

    @Column(name = "obscured", nullable = false)
    private boolean obscured;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_state_hand_card", joinColumns = @JoinColumn(name = "player_state_id"))
    @OrderColumn(name = "card_position")
    private List<PlayerHandCardValue> hand;

    protected PlayerStateJpaEntity() {}

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

    boolean isObscured() {
        return obscured;
    }

    void setObscured(boolean obscured) {
        this.obscured = obscured;
    }

    List<PlayerHandCardValue> getHand() {
        return hand;
    }

    void setHand(List<PlayerHandCardValue> hand) {
        this.hand = hand;
    }
}
