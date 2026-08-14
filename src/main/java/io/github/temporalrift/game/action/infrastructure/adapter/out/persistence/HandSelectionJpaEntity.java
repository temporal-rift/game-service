package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "hand_selection")
class HandSelectionJpaEntity {
    @Id
    UUID id;

    @Column(name = "game_id", nullable = false)
    UUID gameId;

    @Column(name = "era_number", nullable = false)
    int eraNumber;

    @Column(name = "player_id", nullable = false)
    UUID playerId;

    @Column(nullable = false)
    String status;

    @Column(name = "selection_expires_at", nullable = false)
    Instant selectionExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dealt_cards", columnDefinition = "jsonb", nullable = false)
    List<StoredHandSelectionCard> dealtCards;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_cards", columnDefinition = "jsonb")
    List<StoredHandSelectionCard> selectedCards;

    @Column(name = "selection_origin")
    String selectionOrigin;
}
