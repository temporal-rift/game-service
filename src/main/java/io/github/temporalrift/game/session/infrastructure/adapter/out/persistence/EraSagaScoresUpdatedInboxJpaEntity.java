package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.game.shared.ScoresUpdated;

@Entity
@Table(name = "era_saga_scores_updated_inbox")
class EraSagaScoresUpdatedInboxJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private ScoresUpdated payload;

    protected EraSagaScoresUpdatedInboxJpaEntity() {}

    static EraSagaScoresUpdatedInboxJpaEntity fromDomain(ScoresUpdated event) {
        var entity = new EraSagaScoresUpdatedInboxJpaEntity();
        entity.id = UUID.randomUUID();
        entity.gameId = event.gameId();
        entity.eraNumber = event.eraNumber();
        entity.payload = event;
        return entity;
    }

    ScoresUpdated toDomain() {
        return payload;
    }
}
