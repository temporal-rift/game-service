package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

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

import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyAlreadyStartedException;
import io.github.temporalrift.game.session.domain.lobby.LobbyFullException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class JoinLobbyCommandHandlerTest {

    static final UUID lobbyId = UUID.randomUUID();
    static final UUID playerId = UUID.randomUUID();
    static final UUID hostPlayerId = UUID.randomUUID();

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    Lobby lobby;

    @InjectMocks
    JoinLobbyCommandHandler handler;

    /**
     * Stubs needed when the handler runs to completion (join succeeds).
     */
    private void stubSuccessfulJoin() {
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        given(lobbyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(lobby.id()).willReturn(lobbyId);
    }

    @Test
    @DisplayName("saves the lobby after the player joins")
    void handle_savesLobbyAfterJoin() {
        // given
        stubSuccessfulJoin();
        given(lobby.currentPlayers()).willReturn(List.of());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        handler.handle(command);

        // then
        then(lobbyRepository).should().save(lobby);
    }

    @Test
    @DisplayName("joins the player with name and playerId from the command")
    void handle_joinsPlayerWithDetailsFromCommand() {
        // given
        stubSuccessfulJoin();
        given(lobby.currentPlayers()).willReturn(List.of());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        handler.handle(command);

        // then
        var captor = ArgumentCaptor.forClass(LobbyPlayer.class);
        then(lobby).should().join(captor.capture());
        assertThat(captor.getValue().playerId()).isEqualTo(playerId);
        assertThat(captor.getValue().playerName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("returns result with lobbyId and playerId from command")
    void handle_returnsResultWithLobbyIdAndPlayerId() {
        // given
        stubSuccessfulJoin();
        given(lobby.currentPlayers()).willReturn(List.of());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        var result = handler.handle(command);

        // then
        assertThat(result.lobbyId()).isEqualTo(lobbyId);
        assertThat(result.playerId()).isEqualTo(playerId);
    }

    @Test
    @DisplayName("result currentPlayers reflects the lobby state after join")
    void handle_returnsCurrentPlayersFromLobbyAfterJoin() {
        // given
        stubSuccessfulJoin();
        var existingPlayer = new LobbyPlayer(hostPlayerId, "Bob", null, null, true);
        given(lobby.currentPlayers()).willReturn(List.of(existingPlayer));
        given(lobby.hostPlayerId()).willReturn(hostPlayerId);
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when
        var result = handler.handle(command);

        // then
        assertThat(result.currentPlayers()).hasSize(1);
        assertThat(result.currentPlayers().getFirst().playerId()).isEqualTo(existingPlayer.playerId());
        assertThat(result.currentPlayers().getFirst().isHost()).isTrue();
    }

    @Test
    @DisplayName("throws LobbyNotFoundException when lobby does not exist")
    void handle_lobbyNotFound_throwsLobbyNotFoundException() {
        // given
        given(lobbyRepository.findById(any())).willReturn(Optional.empty());
        var command = new JoinLobbyUseCase.Command(UUID.randomUUID(), playerId, "Alice");

        // when / then
        assertThatExceptionOfType(LobbyNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("propagates LobbyFullException thrown by the aggregate")
    void handle_lobbyFull_propagatesLobbyFullException() {
        // given
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        willThrow(new LobbyFullException()).given(lobby).join(any());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when / then
        assertThatExceptionOfType(LobbyFullException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("propagates LobbyAlreadyStartedException thrown by the aggregate")
    void handle_lobbyAlreadyStarted_propagatesLobbyAlreadyStartedException() {
        // given
        given(lobbyRepository.findById(lobbyId)).willReturn(Optional.of(lobby));
        willThrow(new LobbyAlreadyStartedException()).given(lobby).join(any());
        var command = new JoinLobbyUseCase.Command(lobbyId, playerId, "Alice");

        // when / then
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> handler.handle(command));
    }
}
