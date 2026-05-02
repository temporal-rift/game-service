package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "end_game_saga_state")
public class EndGameSagaStateJpaEntity {

    @Id
    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "player_ids", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = UuidListConverter.class)
    private List<UUID> playerIds;

    protected EndGameSagaStateJpaEntity() {}

    public UUID getGameId() {
        return gameId;
    }

    public void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<UUID> getPlayerIds() {
        return playerIds;
    }

    public void setPlayerIds(List<UUID> playerIds) {
        this.playerIds = playerIds;
    }
}
