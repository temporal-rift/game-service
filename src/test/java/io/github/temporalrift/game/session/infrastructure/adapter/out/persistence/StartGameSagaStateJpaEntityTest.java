package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;

class StartGameSagaStateJpaEntityTest {

    @Test
    @DisplayName("getters return the values set via setters")
    void settersAndGetters_roundTrip() {
        // given
        var entity = new StartGameSagaStateJpaEntity();
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        var assignments = List.of(new FactionAssignment(UUID.randomUUID(), Faction.REVISIONISTS));

        // when
        entity.setId(id);
        entity.setGameId(gameId);
        entity.setLobbyId(lobbyId);
        entity.setStatus("RUNNING");
        entity.setCurrentStep(2);
        entity.setFactionAssignments(assignments);

        // then
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getGameId()).isEqualTo(gameId);
        assertThat(entity.getLobbyId()).isEqualTo(lobbyId);
        assertThat(entity.getStatus()).isEqualTo("RUNNING");
        assertThat(entity.getCurrentStep()).isEqualTo(2);
        assertThat(entity.getFactionAssignments()).isEqualTo(assignments);
    }

    @Test
    @DisplayName("nullable fields accept null without error")
    void nullableFields_acceptNull() {
        // given
        var entity = new StartGameSagaStateJpaEntity();

        // when
        entity.setCurrentStep(null);
        entity.setFactionAssignments(null);

        // then
        assertThat(entity.getCurrentStep()).isNull();
        assertThat(entity.getFactionAssignments()).isNull();
    }
}
