package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.NoSuchElementException;
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
import io.github.temporalrift.events.session.PlayerLeftLobby;
import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.HostCannotLeaveException;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@ExtendWith(MockitoExtension.class)
class LeaveLobbyCommandHandlerTest {

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    Lobby lobby;

    @InjectMocks
    LeaveLobbyCommandHandler handler;

    static final UUID lobbyId = UUID.randomUUID();
    static final UUID gameId = UUID.randomUUID();
    static final UUID playerId = UUID.randomUUID();

    /** Stubs needed when the handler runs to completion (leave succeeds). */
    private void stubSuccessfulLeave() {
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        given(lobbyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(lobby.id()).willReturn(lobbyId);
        given(lobby.gameId()).willReturn(gameId);
    }

    @Test
    @DisplayName("calls leave on the lobby with the playerId from the command")
    void execute_callsLeaveWithPlayerIdFromCommand() {
        // given
        stubSuccessfulLeave();
        var command = new LeaveLobbyUseCase.Command(lobbyId, playerId);

        // when
        handler.execute(command);

        // then
        var captor = ArgumentCaptor.forClass(UUID.class);
        then(lobby).should().leave(captor.capture());
        assertThat(captor.getValue()).isEqualTo(playerId);
    }

    @Test
    @DisplayName("saves the lobby after the player leaves")
    void execute_savesLobbyAfterLeave() {
        // given
        stubSuccessfulLeave();
        var command = new LeaveLobbyUseCase.Command(lobbyId, playerId);

        // when
        handler.execute(command);

        // then
        then(lobbyRepository).should().save(lobby);
    }

    @Test
    @DisplayName("publishes PlayerLeftLobby with correct lobbyId and playerId")
    void execute_publishesPlayerLeftLobbyEvent() {
        // given
        stubSuccessfulLeave();
        var command = new LeaveLobbyUseCase.Command(lobbyId, playerId);

        // when
        handler.execute(command);

        // then
        var captor = ArgumentCaptor.forClass(EventEnvelope.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("session.PlayerLeftLobby");
        assertThat(captor.getValue().gameId()).isEqualTo(gameId);
        var payload = (PlayerLeftLobby) captor.getValue().payload();
        assertThat(payload.lobbyId()).isEqualTo(lobbyId);
        assertThat(payload.playerId()).isEqualTo(playerId);
    }

    @Test
    @DisplayName("throws NoSuchElementException when lobby does not exist")
    void execute_lobbyNotFound_throwsNoSuchElementException() {
        // given
        given(lobbyRepository.findById(any())).willReturn(Optional.empty());
        var command = new LeaveLobbyUseCase.Command(UUID.randomUUID(), playerId);

        // when / then
        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> handler.execute(command));
    }

    @Test
    @DisplayName("propagates HostCannotLeaveException thrown by the aggregate")
    void execute_hostLeaves_propagatesHostCannotLeaveException() {
        // given
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        willThrow(new HostCannotLeaveException()).given(lobby).leave(any());
        var command = new LeaveLobbyUseCase.Command(lobbyId, playerId);

        // when / then
        assertThatExceptionOfType(HostCannotLeaveException.class).isThrownBy(() -> handler.execute(command));
    }
}
