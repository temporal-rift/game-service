package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.GameStartCancelled;
import io.github.temporalrift.events.session.GameStartFailed;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

@ExtendWith(MockitoExtension.class)
class StartGameSagaCompensatorTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID SAGA_ID = UUID.randomUUID();
    static final StartGameSagaState RUNNING_STATE =
            new StartGameSagaState(SAGA_ID, GAME_ID, LOBBY_ID, StartGameSagaStatus.RUNNING, 0, List.of());

    static final String REASON = "duplicate faction";

    @Mock
    StartGameSagaRepository startGameSagaRepository;

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    Lobby lobby;

    @InjectMocks
    StartGameSagaCompensator compensator;

    @Test
    @DisplayName("saga in RUNNING status — marks CANCELLED and publishes GameStartCancelled")
    void cancel_sagaRunning_marksCancelledAndPublishesEvent() {
        // given
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(RUNNING_STATE));

        // when
        compensator.cancel(GAME_ID);

        // then
        then(startGameSagaRepository).should().save(RUNNING_STATE.withStatus(StartGameSagaStatus.CANCELLED));

        var captor = ArgumentCaptor.forClass(EventEnvelope.class);
        then(eventPublisher).should().publish(captor.capture());
        var envelope = captor.getValue();
        assertThat(envelope.payload()).isInstanceOf(GameStartCancelled.class);
        var payload = (GameStartCancelled) envelope.payload();
        assertThat(payload.gameId()).isEqualTo(GAME_ID);
        assertThat(payload.lobbyId()).isEqualTo(LOBBY_ID);
    }

    @Test
    @DisplayName("saga already COMPLETED — takes no action")
    void cancel_sagaCompleted_takesNoAction() {
        // given
        var completedState = RUNNING_STATE.withStatus(StartGameSagaStatus.COMPLETED);
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(completedState));

        // when
        compensator.cancel(GAME_ID);

        // then
        then(startGameSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(startGameSagaRepository).shouldHaveNoMoreInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("no saga found for gameId — takes no action")
    void cancel_sagaNotFound_takesNoAction() {
        // given
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());

        // when
        compensator.cancel(GAME_ID);

        // then
        then(startGameSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(startGameSagaRepository).shouldHaveNoMoreInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("compensate — marks COMPENSATING, resets lobby, publishes GameStartFailed, marks FAILED")
    void compensate_sagaFound_fullCompensationFlow() {
        // given
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(RUNNING_STATE));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.id()).willReturn(LOBBY_ID);

        // when
        compensator.compensate(GAME_ID, REASON);

        // then
        var ordered = inOrder(startGameSagaRepository, lobbyRepository, eventPublisher);
        then(startGameSagaRepository).should(ordered).save(RUNNING_STATE.withStatus(StartGameSagaStatus.COMPENSATING));
        then(lobby).should().resetFactionAssignments();
        then(lobbyRepository).should(ordered).save(lobby);

        var captor = ArgumentCaptor.forClass(EventEnvelope.class);
        then(eventPublisher).should(ordered).publish(captor.capture());
        assertThat(captor.getValue().payload()).isInstanceOf(GameStartFailed.class);
        var payload = (GameStartFailed) captor.getValue().payload();
        assertThat(payload.gameId()).isEqualTo(GAME_ID);
        assertThat(payload.lobbyId()).isEqualTo(LOBBY_ID);
        assertThat(payload.reason()).isEqualTo(REASON);

        then(startGameSagaRepository).should(ordered).save(RUNNING_STATE.withStatus(StartGameSagaStatus.FAILED));
    }

    @Test
    @DisplayName("compensate — lobby not found — throws IllegalStateException")
    void compensate_lobbyNotFound_throwsIllegalStateException() {
        // given
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(RUNNING_STATE));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.empty());

        // when / then
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> compensator.compensate(GAME_ID, REASON));
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("compensate — no saga found for gameId — takes no action")
    void compensate_sagaNotFound_takesNoAction() {
        // given
        given(startGameSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());

        // when
        compensator.compensate(GAME_ID, REASON);

        // then
        then(startGameSagaRepository).should().findByGameIdWithLock(GAME_ID);
        then(startGameSagaRepository).shouldHaveNoMoreInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }
}
