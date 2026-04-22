package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.github.temporalrift.game.session.domain.saga.FactionAssignment;

@Entity
@Table(name = "start_game_saga_state")
public class StartGameSagaStateJpaEntity {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false, unique = true)
    private UUID gameId;

    @Column(name = "lobby_id", nullable = false)
    private UUID lobbyId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "current_step")
    private Integer currentStep;

    @Column(name = "context", columnDefinition = "jsonb")
    @Convert(converter = FactionAssignmentListConverter.class)
    private List<FactionAssignment> factionAssignments; // nullable

    protected StartGameSagaStateJpaEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getGameId() {
        return gameId;
    }

    public void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    public UUID getLobbyId() {
        return lobbyId;
    }

    public void setLobbyId(UUID lobbyId) {
        this.lobbyId = lobbyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(Integer currentStep) {
        this.currentStep = currentStep;
    }

    public List<FactionAssignment> getFactionAssignments() {
        return factionAssignments;
    }

    public void setFactionAssignments(List<FactionAssignment> factionAssignments) {
        this.factionAssignments = factionAssignments;
    }
}
