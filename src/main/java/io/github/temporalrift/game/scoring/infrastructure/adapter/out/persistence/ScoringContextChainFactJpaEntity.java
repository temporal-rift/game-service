package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_chain_fact")
class ScoringContextChainFactJpaEntity extends GamePlayerScopedJpaEntity {

    @Column(name = "chain_id", nullable = false)
    private UUID chainId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    protected ScoringContextChainFactJpaEntity() {}

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

    int getEraNumber() {
        return eraNumber;
    }

    void setEraNumber(int eraNumber) {
        this.eraNumber = eraNumber;
    }

    boolean isConsumed() {
        return consumed;
    }

    void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }
}
