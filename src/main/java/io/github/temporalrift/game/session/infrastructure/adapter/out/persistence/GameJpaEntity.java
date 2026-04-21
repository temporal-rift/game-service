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
@Table(name = "game")
class GameJpaEntity {

    @Id
    private UUID id;

    @Column(name = "lobby_id", nullable = false)
    private UUID lobbyId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "era_counter", nullable = false)
    private int eraCounter;

    @Column(name = "cascaded_paradox_counter", nullable = false)
    private int cascadedParadoxCounter;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "game_deck_entry", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "event_id", nullable = false)
    @OrderColumn(name = "list_order")
    private List<UUID> eventDeck = new ArrayList<>();

    protected GameJpaEntity() {}

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getLobbyId() {
        return lobbyId;
    }

    void setLobbyId(UUID lobbyId) {
        this.lobbyId = lobbyId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    int getEraCounter() {
        return eraCounter;
    }

    void setEraCounter(int eraCounter) {
        this.eraCounter = eraCounter;
    }

    int getCascadedParadoxCounter() {
        return cascadedParadoxCounter;
    }

    void setCascadedParadoxCounter(int cascadedParadoxCounter) {
        this.cascadedParadoxCounter = cascadedParadoxCounter;
    }

    List<UUID> getEventDeck() {
        return eventDeck;
    }

    void setEventDeck(List<UUID> eventDeck) {
        this.eventDeck = new ArrayList<>(eventDeck);
    }
}
