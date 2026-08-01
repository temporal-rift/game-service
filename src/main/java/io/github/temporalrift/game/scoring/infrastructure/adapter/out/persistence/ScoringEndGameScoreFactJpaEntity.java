package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_end_game_score_fact")
class ScoringEndGameScoreFactJpaEntity extends GamePlayerScopedJpaEntity {

    @Column(name = "reason", nullable = false)
    private String reason;

    protected ScoringEndGameScoreFactJpaEntity() {}

    String getReason() {
        return reason;
    }

    void setReason(String reason) {
        this.reason = reason;
    }
}
