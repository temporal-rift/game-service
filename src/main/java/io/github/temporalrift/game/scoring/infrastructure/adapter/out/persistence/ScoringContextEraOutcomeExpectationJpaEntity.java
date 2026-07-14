package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_era_outcome_expectation")
class ScoringContextEraOutcomeExpectationJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "expected_outcome_count", nullable = false)
    private int expectedOutcomeCount;

    protected ScoringContextEraOutcomeExpectationJpaEntity() {}

    int getExpectedOutcomeCount() {
        return expectedOutcomeCount;
    }

    void setExpectedOutcomeCount(int expectedOutcomeCount) {
        this.expectedOutcomeCount = expectedOutcomeCount;
    }
}
