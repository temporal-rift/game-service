package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_chain_fact")
class ScoringContextChainFactJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "chain_id", nullable = false)
    private UUID chainId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    protected ScoringContextChainFactJpaEntity() {}

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

    UUID getChainId() {
        return chainId;
    }

    void setChainId(UUID chainId) {
        this.chainId = chainId;
    }

    String getReason() {
        return reason;
    }

    void setReason(String reason) {
        this.reason = reason;
    }

    boolean isConsumed() {
        return consumed;
    }

    void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }
}
