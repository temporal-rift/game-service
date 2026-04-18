package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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

import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.session.application.saga.GameStartSaga;
import io.github.temporalrift.game.session.domain.lobby.DisconnectedPlayersException;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotHostException;
import io.github.temporalrift.game.session.domain.lobby.StartOutcome;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class StartGameCommandHandlerTest {

    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID REQUESTING_PLAYER_ID = UUID.randomUUID();

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    GameStartSaga gameStartSaga;

    @Mock
    Lobby lobby;

    @InjectMocks
    StartGameCommandHandler handler;

    @Test
    @DisplayName("host starts game — invokes saga and returns pre-assigned gameId")
    void handle_hostStartsGame_invokesSagaAndReturnsGameId() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.requestStart(REQUESTING_PLAYER_ID)).willReturn(new StartOutcome.GameStarted());
        given(lobby.gameId()).willReturn(GAME_ID);
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when
        var result = handler.handle(command);

        // then
        assertThat(result.gameId()).isEqualTo(GAME_ID);
        then(gameStartSaga).should().start(GAME_ID, lobby);
    }

    @Test
    @DisplayName("lobby not found — throws LobbyNotFoundException")
    void handle_lobbyNotFound_throwsLobbyNotFoundException() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.empty());
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(LobbyNotFoundException.class).isThrownBy(() -> handler.handle(command));
        then(gameStartSaga).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("requesting player is not host — throws NotHostException")
    void handle_notHost_throwsNotHostException() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.requestStart(REQUESTING_PLAYER_ID)).willReturn(new StartOutcome.NotHost());
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(NotHostException.class).isThrownBy(() -> handler.handle(command));
        then(gameStartSaga).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("not enough players — throws NotEnoughPlayersException")
    void handle_notEnoughPlayers_throwsNotEnoughPlayersException() {
        // given
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.requestStart(REQUESTING_PLAYER_ID)).willReturn(new StartOutcome.NotEnoughPlayers(2, 3));
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(NotEnoughPlayersException.class).isThrownBy(() -> handler.handle(command));
        then(gameStartSaga).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("lobby has disconnected players — throws DisconnectedPlayersException")
    void handle_disconnectedPlayers_throwsDisconnectedPlayersException() {
        // given
        var disconnectedId = UUID.randomUUID();
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.requestStart(REQUESTING_PLAYER_ID))
                .willReturn(new StartOutcome.HasDisconnectedPlayers(List.of(disconnectedId)));
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(DisconnectedPlayersException.class).isThrownBy(() -> handler.handle(command));
        then(gameStartSaga).shouldHaveNoInteractions();
    }
}
