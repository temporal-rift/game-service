package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class GamePlayerScopedJpaEntity extends GameScopedJpaEntity {

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
}
