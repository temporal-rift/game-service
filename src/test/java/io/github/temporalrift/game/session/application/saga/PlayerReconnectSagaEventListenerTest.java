package io.github.temporalrift.game.session.application.saga;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class PlayerReconnectSagaEventListenerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();

    @Mock
    PlayerReconnectSaga saga;

    @Mock
    PlayerReconnectTimerScheduler timerScheduler;

    @Mock
    PlayerReconnectSagaStateManager stateManager;

    @Mock
    GameRepository gameRepository;

    @Mock
    LobbyRepository lobbyRepository;

    private Lobby startedLobby() {
        var player = new LobbyPlayer(PLAYER_ID, "Alice", null, Instant.now(), false);
        return Lobby.reconstitute(
                LOBBY_ID,
                GAME_ID,
                PLAYER_ID,
                List.of(player),
                LobbyStatus.STARTED,
                new LobbyConfig("ABCD", 3, 5, java.time.Clock.systemUTC()));
    }

    @Test
    @DisplayName("onPlayerDisconnected starts grace-period saga only for started games without active grace period")
    void onPlayerDisconnected_startedGameWithoutActiveGrace_startsSaga() {
        // given
        var listener = new PlayerReconnectSagaEventListener(
                saga, timerScheduler, stateManager, gameRepository, lobbyRepository);
        var event = new PlayerDisconnectedApplicationEvent(GAME_ID, PLAYER_ID);
        var game = new Game(GAME_ID, LOBBY_ID, List.of());
        var lobby = startedLobby();
        var result = new PlayerReconnectSaga.StartResult(
                UUID.randomUUID(), Instant.now().plusSeconds(30));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(stateManager.hasActiveGracePeriod(GAME_ID, PLAYER_ID)).willReturn(false);
        given(saga.start(GAME_ID, PLAYER_ID)).willReturn(result);

        // when
        listener.onPlayerDisconnected(event);

        // then
        then(saga).should().start(GAME_ID, PLAYER_ID);
        then(timerScheduler).should().scheduleAfterCommit(result);
    }

    @Test
    @DisplayName("onPlayerDisconnected does nothing when a grace period is already active")
    void onPlayerDisconnected_activeGracePeriod_doesNothing() {
        // given
        var listener = new PlayerReconnectSagaEventListener(
                saga, timerScheduler, stateManager, gameRepository, lobbyRepository);
        var event = new PlayerDisconnectedApplicationEvent(GAME_ID, PLAYER_ID);
        var game = new Game(GAME_ID, LOBBY_ID, List.of());
        var lobby = startedLobby();
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(stateManager.hasActiveGracePeriod(GAME_ID, PLAYER_ID)).willReturn(true);

        // when
        listener.onPlayerDisconnected(event);

        // then
        then(saga).should(never()).start(GAME_ID, PLAYER_ID);
        then(timerScheduler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("onPlayerDisconnected does nothing when lobby is not started")
    void onPlayerDisconnected_lobbyNotStarted_doesNothing() {
        // given
        var listener = new PlayerReconnectSagaEventListener(
                saga, timerScheduler, stateManager, gameRepository, lobbyRepository);
        var event = new PlayerDisconnectedApplicationEvent(GAME_ID, PLAYER_ID);
        var game = new Game(GAME_ID, LOBBY_ID, List.of());
        var player = new LobbyPlayer(PLAYER_ID, "Alice", null, Instant.now(), false);
        var waitingLobby = Lobby.reconstitute(
                LOBBY_ID,
                GAME_ID,
                PLAYER_ID,
                List.of(player),
                LobbyStatus.WAITING,
                new LobbyConfig("ABCD", 3, 5, java.time.Clock.systemUTC()));
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(waitingLobby));

        // when
        listener.onPlayerDisconnected(event);

        // then
        then(saga).should(never()).start(GAME_ID, PLAYER_ID);
        then(timerScheduler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("onPlayerReconnected delegates reconnect handling to the saga")
    void onPlayerReconnected_delegatesToSaga() {
        // given
        var listener = new PlayerReconnectSagaEventListener(
                saga, timerScheduler, stateManager, gameRepository, lobbyRepository);
        var event = new PlayerReconnectedApplicationEvent(GAME_ID, PLAYER_ID);

        // when
        listener.onPlayerReconnected(event);

        // then
        then(saga).should().handleReconnect(GAME_ID, PLAYER_ID);
    }
}
