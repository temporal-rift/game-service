package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "session_foresight_reveal")
class ForesightRevealJpaEntity {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "next_era_number", nullable = false)
    private int nextEraNumber;

    @Column(name = "empty_reason")
    private String emptyReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_foresight_reveal_entry", joinColumns = @JoinColumn(name = "reveal_id"))
    @Column(name = "catalog_event_id", nullable = false)
    @OrderColumn(name = "list_order")
    private List<UUID> catalogEventIds = new ArrayList<>();

    protected ForesightRevealJpaEntity() {}

    static ForesightRevealJpaEntity from(
            UUID gameId,
            int eraNumber,
            UUID playerId,
            int nextEraNumber,
            List<UUID> catalogEventIds,
            String emptyReason) {
        var entity = new ForesightRevealJpaEntity();
        entity.id = UUID.randomUUID();
        entity.gameId = gameId;
        entity.eraNumber = eraNumber;
        entity.playerId = playerId;
        entity.nextEraNumber = nextEraNumber;
        entity.catalogEventIds = new ArrayList<>(catalogEventIds);
        entity.emptyReason = emptyReason;
        return entity;
    }

    UUID gameId() {
        return gameId;
    }

    int eraNumber() {
        return eraNumber;
    }

    UUID playerId() {
        return playerId;
    }

    int nextEraNumber() {
        return nextEraNumber;
    }

    List<UUID> catalogEventIds() {
        return catalogEventIds;
    }

    String emptyReason() {
        return emptyReason;
    }
}
