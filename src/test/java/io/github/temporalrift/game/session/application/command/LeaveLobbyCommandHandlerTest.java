package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
import io.github.temporalrift.events.session.HostTransferred;
import io.github.temporalrift.events.session.LobbyClosed;
import io.github.temporalrift.events.session.PlayerLeftLobby;
import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.LeaveOutcome;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@ExtendWith(MockitoExtension.class)
class LeaveLobbyCommandHandlerTest {

    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    Lobby lobby;

    @InjectMocks
    LeaveLobbyCommandHandler handler;

    private void stubLobby(LeaveOutcome outcome) {
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobbyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.gameId()).willReturn(GAME_ID);
        given(lobby.leave(PLAYER_ID)).willReturn(outcome);
    }

    @Test
    @DisplayName("non-host leaves — publishes PlayerLeftLobby")
    void handle_nonHostLeaves_publishesPlayerLeftLobby() {
        // given
        stubLobby(new LeaveOutcome.NonHostLeft());
        var command = new LeaveLobbyUseCase.Command(LOBBY_ID, PLAYER_ID);

        // when
        handler.handle(command);

        // then
        var captor = forClass(EventEnvelope.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("session.PlayerLeftLobby");
        var payload = (PlayerLeftLobby) captor.getValue().payload();
        assertThat(payload.lobbyId()).isEqualTo(LOBBY_ID);
        assertThat(payload.playerId()).isEqualTo(PLAYER_ID);
    }

    @Test
    @DisplayName("host leaves with others present — publishes HostTransferred then PlayerLeftLobby")
    void handle_hostLeavesWithOthers_publishesHostTransferredAndPlayerLeftLobby() {
        // given
        var newHostId = UUID.randomUUID();
        stubLobby(new LeaveOutcome.HostTransferred(newHostId));
        var command = new LeaveLobbyUseCase.Command(LOBBY_ID, PLAYER_ID);

        // when
        handler.handle(command);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope> captor = forClass(EventEnvelope.class);
        then(eventPublisher).should(org.mockito.BDDMockito.times(2)).publish(captor.capture());
        var published = captor.getAllValues();

        assertThat(published.get(0).eventType()).isEqualTo("session.HostTransferred");
        var transfer = (HostTransferred) published.get(0).payload();
        assertThat(transfer.lobbyId()).isEqualTo(LOBBY_ID);
        assertThat(transfer.previousHostId()).isEqualTo(PLAYER_ID);
        assertThat(transfer.newHostId()).isEqualTo(newHostId);

        assertThat(published.get(1).eventType()).isEqualTo("session.PlayerLeftLobby");
        var left = (PlayerLeftLobby) published.get(1).payload();
        assertThat(left.lobbyId()).isEqualTo(LOBBY_ID);
        assertThat(left.playerId()).isEqualTo(PLAYER_ID);
    }

    @Test
    @DisplayName("host leaves as sole player — publishes LobbyClosed")
    void handle_hostLeavesSolePlayer_publishesLobbyClosed() {
        // given
        stubLobby(new LeaveOutcome.LobbyClosed());
        var command = new LeaveLobbyUseCase.Command(LOBBY_ID, PLAYER_ID);

        // when
        handler.handle(command);

        // then
        var captor = forClass(EventEnvelope.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("session.LobbyClosed");
        var payload = (LobbyClosed) captor.getValue().payload();
        assertThat(payload.lobbyId()).isEqualTo(LOBBY_ID);
        assertThat(payload.gameId()).isEqualTo(GAME_ID);
    }

    @Test
    @DisplayName("lobby not found — throws LobbyNotFoundException")
    void handle_lobbyNotFound_throwsLobbyNotFoundException() {
        // given
        given(lobbyRepository.findById(any())).willReturn(Optional.empty());
        var command = new LeaveLobbyUseCase.Command(UUID.randomUUID(), PLAYER_ID);

        // when / then
        assertThatExceptionOfType(LobbyNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("saves lobby after leave regardless of outcome")
    void handle_savesLobbyAfterLeave() {
        // given
        stubLobby(new LeaveOutcome.NonHostLeft());
        var command = new LeaveLobbyUseCase.Command(LOBBY_ID, PLAYER_ID);

        // when
        handler.handle(command);

        // then
        then(lobbyRepository).should().save(lobby);
    }
}
