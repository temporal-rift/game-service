package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.SpecialAction;

@Entity
@Table(name = "session_activist_declaration")
class SessionActivistDeclarationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "target_event_id", nullable = false)
    private UUID targetEventId;

    @Column(name = "mode", nullable = false)
    private String mode;

    protected SessionActivistDeclarationJpaEntity() {}

    static SessionActivistDeclarationJpaEntity from(
            UUID gameId, int eraNumber, UUID playerId, UUID targetEventId, SpecialAction mode) {
        var entity = new SessionActivistDeclarationJpaEntity();
        entity.id = UUID.randomUUID();
        entity.gameId = gameId;
        entity.eraNumber = eraNumber;
        entity.playerId = playerId;
        entity.targetEventId = targetEventId;
        entity.mode = mode.name();
        return entity;
    }

    UUID playerId() {
        return playerId;
    }

    UUID targetEventId() {
        return targetEventId;
    }
}
