package io.github.temporalrift.game.session.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.session.application.saga.StartGameSaga;
import io.github.temporalrift.game.session.domain.lobby.DisconnectedPlayersException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotLobbyHostException;

@ExtendWith(MockitoExtension.class)
class StartGameCommandHandlerTest {

    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID REQUESTING_PLAYER_ID = UUID.randomUUID();

    @Mock
    StartGameSaga startGameSaga;

    @InjectMocks
    StartGameCommandHandler handler;

    @Test
    @DisplayName("start game delegates to saga and returns gameId")
    void handle_hostStartsGame_invokesSagaAndReturnsGameId() {
        // given
        given(startGameSaga.start(LOBBY_ID, REQUESTING_PLAYER_ID)).willReturn(GAME_ID);
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when
        var result = handler.handle(command);

        // then
        assertThat(result.gameId()).isEqualTo(GAME_ID);
        then(startGameSaga).should().start(LOBBY_ID, REQUESTING_PLAYER_ID);
    }

    @Test
    @DisplayName("lobby not found from saga — propagates LobbyNotFoundException")
    void handle_lobbyNotFound_propagatesLobbyNotFoundException() {
        // given
        given(startGameSaga.start(LOBBY_ID, REQUESTING_PLAYER_ID)).willThrow(new LobbyNotFoundException(LOBBY_ID));
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(LobbyNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("not host from saga — propagates NotLobbyHostException")
    void handle_notHost_propagatesNotLobbyHostException() {
        // given
        given(startGameSaga.start(LOBBY_ID, REQUESTING_PLAYER_ID)).willThrow(new NotLobbyHostException());
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(NotLobbyHostException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("not enough players from saga — propagates NotEnoughPlayersException")
    void handle_notEnoughPlayers_propagatesNotEnoughPlayersException() {
        // given
        given(startGameSaga.start(LOBBY_ID, REQUESTING_PLAYER_ID)).willThrow(new NotEnoughPlayersException(2, 3));
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(NotEnoughPlayersException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("disconnected players from saga — propagates DisconnectedPlayersException")
    void handle_disconnectedPlayers_propagatesDisconnectedPlayersException() {
        // given
        given(startGameSaga.start(LOBBY_ID, REQUESTING_PLAYER_ID))
                .willThrow(new DisconnectedPlayersException(java.util.List.of(UUID.randomUUID())));
        var command = new StartGameUseCase.Command(LOBBY_ID, REQUESTING_PLAYER_ID);

        // when / then
        assertThatExceptionOfType(DisconnectedPlayersException.class).isThrownBy(() -> handler.handle(command));
    }
}
