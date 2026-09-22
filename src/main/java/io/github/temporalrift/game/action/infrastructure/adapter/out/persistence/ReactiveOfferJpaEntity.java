package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GameEraScopedJpaEntity;

@Entity
@Table(name = "reactive_offer")
class ReactiveOfferJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "stabilize_card_instance_id", nullable = false)
    private UUID stabilizeCardInstanceId;

    @Column(name = "detonate_card_instance_id", nullable = false)
    private UUID detonateCardInstanceId;

    @Column(name = "consumed_card_instance_id")
    private UUID consumedCardInstanceId;

    @Column(name = "status", nullable = false)
    private String status;

    protected ReactiveOfferJpaEntity() {}

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    UUID getStabilizeCardInstanceId() {
        return stabilizeCardInstanceId;
    }

    void setStabilizeCardInstanceId(UUID stabilizeCardInstanceId) {
        this.stabilizeCardInstanceId = stabilizeCardInstanceId;
    }

    UUID getDetonateCardInstanceId() {
        return detonateCardInstanceId;
    }

    void setDetonateCardInstanceId(UUID detonateCardInstanceId) {
        this.detonateCardInstanceId = detonateCardInstanceId;
    }

    UUID getConsumedCardInstanceId() {
        return consumedCardInstanceId;
    }

    void setConsumedCardInstanceId(UUID consumedCardInstanceId) {
        this.consumedCardInstanceId = consumedCardInstanceId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }
}
