package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

@ExtendWith(MockitoExtension.class)
class StartGameSagaAdapterTest {

    @Mock
    StartGameSagaStateJpaRepository jpaRepository;

    @InjectMocks
    StartGameSagaAdapter adapter;

    static StartGameSagaState sagaState() {
        return new StartGameSagaState(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                StartGameSagaStatus.RUNNING,
                1,
                List.of(new FactionAssignment(UUID.randomUUID(), Faction.WEAVERS)));
    }

    static StartGameSagaStateJpaEntity entityFor(StartGameSagaState saga) {
        var e = new StartGameSagaStateJpaEntity();
        e.setId(saga.sagaId());
        e.setGameId(saga.gameId());
        e.setLobbyId(saga.lobbyId());
        e.setStatus(saga.status().name());
        e.setCurrentStep(saga.currentStep());
        e.setFactionAssignments(saga.factionAssignments());
        return e;
    }

    @Test
    @DisplayName("save persists entity and returns the original saga state")
    void save_persistsAndReturnsSaga() {
        // given
        var saga = sagaState();
        given(jpaRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        var result = adapter.save(saga);

        // then
        assertThat(result).isEqualTo(saga);
        then(jpaRepository).should().save(any(StartGameSagaStateJpaEntity.class));
    }

    @Test
    @DisplayName("findByGameId returns mapped domain object when entity exists")
    void findByGameId_found_returnsMappedState() {
        // given
        var saga = sagaState();
        given(jpaRepository.findByGameId(saga.gameId())).willReturn(Optional.of(entityFor(saga)));

        // when
        var result = adapter.findByGameId(saga.gameId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().gameId()).isEqualTo(saga.gameId());
        assertThat(result.get().status()).isEqualTo(StartGameSagaStatus.RUNNING);
    }

    @Test
    @DisplayName("findByGameId returns empty when no entity exists")
    void findByGameId_notFound_returnsEmpty() {
        // given
        var gameId = UUID.randomUUID();
        given(jpaRepository.findByGameId(gameId)).willReturn(Optional.empty());

        // when
        var result = adapter.findByGameId(gameId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByGameIdWithLock returns mapped domain object when entity exists")
    void findByGameIdWithLock_found_returnsMappedState() {
        // given
        var saga = sagaState();
        given(jpaRepository.findByGameIdWithLock(saga.gameId())).willReturn(Optional.of(entityFor(saga)));

        // when
        var result = adapter.findByGameIdWithLock(saga.gameId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().sagaId()).isEqualTo(saga.sagaId());
        assertThat(result.get().factionAssignments()).isEqualTo(saga.factionAssignments());
    }

    @Test
    @DisplayName("findByGameIdWithLock returns empty when no entity exists")
    void findByGameIdWithLock_notFound_returnsEmpty() {
        // given
        var gameId = UUID.randomUUID();
        given(jpaRepository.findByGameIdWithLock(gameId)).willReturn(Optional.empty());

        // when
        var result = adapter.findByGameIdWithLock(gameId);

        // then
        assertThat(result).isEmpty();
    }
}
