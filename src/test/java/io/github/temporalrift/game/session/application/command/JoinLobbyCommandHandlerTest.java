package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
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
import io.github.temporalrift.events.session.PlayerJoinedLobby;
import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyAlreadyStartedException;
import io.github.temporalrift.game.session.domain.lobby.LobbyFullException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@ExtendWith(MockitoExtension.class)
class JoinLobbyCommandHandlerTest {

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    Lobby lobby;

    @InjectMocks
    JoinLobbyCommandHandler handler;

    static final UUID lobbyId = UUID.randomUUID();
    static final UUID gameId = UUID.randomUUID();
    static final UUID playerId = UUID.randomUUID();
    static final UUID hostPlayerId = UUID.randomUUID();

    /** Stubs needed when the handler runs to completion (join succeeds). */
    private void stubSuccessfulJoin() {
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        given(lobbyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(lobby.id()).willReturn(lobbyId);
        given(lobby.gameId()).willReturn(gameId);
    }

    @Test
    @DisplayName("saves the lobby after the player joins")
    void execute_savesLobbyAfterJoin() {
        // given
        stubSuccessfulJoin();
        given(lobby.currentPlayers()).willReturn(List.of());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        handler.execute(command);

        // then
        then(lobbyRepository).should().save(lobby);
    }

    @Test
    @DisplayName("joins the player with name and playerId from the command")
    void execute_joinsPlayerWithDetailsFromCommand() {
        // given
        stubSuccessfulJoin();
        given(lobby.currentPlayers()).willReturn(List.of());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        handler.execute(command);

        // then
        var captor = ArgumentCaptor.forClass(LobbyPlayer.class);
        then(lobby).should().join(captor.capture());
        assertThat(captor.getValue().playerId()).isEqualTo(playerId);
        assertThat(captor.getValue().playerName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("publishes PlayerJoinedLobby with correct lobbyId, playerId, and playerName")
    void execute_publishesPlayerJoinedLobbyEvent() {
        // given
        stubSuccessfulJoin();
        given(lobby.currentPlayers()).willReturn(List.of());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        handler.execute(command);

        // then
        var captor = ArgumentCaptor.forClass(EventEnvelope.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("session.PlayerJoinedLobby");
        assertThat(captor.getValue().gameId()).isEqualTo(gameId);
        var payload = (PlayerJoinedLobby) captor.getValue().payload();
        assertThat(payload.lobbyId()).isEqualTo(lobbyId);
        assertThat(payload.playerId()).isEqualTo(playerId);
        assertThat(payload.playerName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("returns result with lobbyId and playerId from command")
    void execute_returnsResultWithLobbyIdAndPlayerId() {
        // given
        stubSuccessfulJoin();
        given(lobby.currentPlayers()).willReturn(List.of());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        var result = handler.execute(command);

        // then
        assertThat(result.lobbyId()).isEqualTo(lobbyId);
        assertThat(result.playerId()).isEqualTo(playerId);
    }

    @Test
    @DisplayName("result currentPlayers reflects the lobby state after join")
    void execute_returnsCurrentPlayersFromLobbyAfterJoin() {
        // given
        stubSuccessfulJoin();
        var existingPlayer = new LobbyPlayer(hostPlayerId, "Bob", null, null);
        given(lobby.currentPlayers()).willReturn(List.of(existingPlayer));
        given(lobby.hostPlayerId()).willReturn(hostPlayerId);
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        var result = handler.execute(command);

        // then
        assertThat(result.currentPlayers()).hasSize(1);
        assertThat(result.currentPlayers().getFirst().playerId()).isEqualTo(existingPlayer.playerId());
        assertThat(result.currentPlayers().getFirst().isHost()).isTrue();
    }

    @Test
    @DisplayName("throws NoSuchElementException when lobby does not exist")
    void execute_lobbyNotFound_throwsNoSuchElementException() {
        // given
        given(lobbyRepository.findById(any())).willReturn(Optional.empty());
        var command = new JoinLobbyUseCase.Command(UUID.randomUUID(), playerId, "Alice");

        // when / then
        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> handler.execute(command));
    }

    @Test
    @DisplayName("propagates LobbyFullException thrown by the aggregate")
    void execute_lobbyFull_propagatesLobbyFullException() {
        // given
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        willThrow(new LobbyFullException()).given(lobby).join(any());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when / then
        assertThatExceptionOfType(LobbyFullException.class).isThrownBy(() -> handler.execute(command));
    }

    @Test
    @DisplayName("propagates LobbyAlreadyStartedException thrown by the aggregate")
    void execute_lobbyAlreadyStarted_propagatesLobbyAlreadyStartedException() {
        // given
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        willThrow(new LobbyAlreadyStartedException()).given(lobby).join(any());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when / then
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> handler.execute(command));
    }
}
