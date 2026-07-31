package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;

@Entity
@Table(name = "scoring_timeline_resolution_barrier")
class ScoringTimelineResolutionBarrierJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private EraResolutionCompleted payload;

    protected ScoringTimelineResolutionBarrierJpaEntity() {}

    static ScoringTimelineResolutionBarrierJpaEntity fromDomain(EraResolutionCompleted resolution) {
        var entity = new ScoringTimelineResolutionBarrierJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(resolution.gameId());
        entity.setEraNumber(resolution.eraNumber());
        entity.payload = resolution;
        return entity;
    }

    EraResolutionCompleted payload() {
        return payload;
    }
}
