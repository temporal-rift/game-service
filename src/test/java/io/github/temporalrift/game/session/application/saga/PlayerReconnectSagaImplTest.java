package io.github.temporalrift.game.session.application.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.GameEndedAbnormally;
import io.github.temporalrift.events.session.PlayerAbandoned;
import io.github.temporalrift.events.session.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@ExtendWith(MockitoExtension.class)
class PlayerReconnectSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final UUID SAGA_ID = UUID.randomUUID();
    static final int GRACE_SECONDS = 30;

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    GameRepository gameRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    PlayerReconnectSagaStateManager stateManager;

    @Mock
    GameRulesPort gameRules;

    @Mock
    TaskScheduler taskScheduler;

    @Mock
    @SuppressWarnings("rawtypes")
    ScheduledFuture scheduledFuture;

    @InjectMocks
    PlayerReconnectSagaImpl saga;

    private static EventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }

    private Game stubGame() {
        var game = new Game(GAME_ID, LOBBY_ID, List.of());
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        return game;
    }

    private Lobby stubStartedLobby(boolean playerConnected) {
        var player = new LobbyPlayer(PLAYER_ID, "Alice", null, Instant.now(), playerConnected);
        var config = new LobbyConfig("ABCD", 3, 5, java.time.Clock.systemUTC());
        var lobby = Lobby.reconstitute(LOBBY_ID, GAME_ID, PLAYER_ID, List.of(player), LobbyStatus.STARTED, config);
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(lobby));
        return lobby;
    }

    @Test
    @DisplayName("start — persists GRACE_PERIOD state, marks lobby disconnected, publishes PlayerDisconnected")
    void start_happyPath_persistsStateMarksLobbyAndPublishesPlayerDisconnected() {
        // given
        stubGame();
        stubStartedLobby(true);
        given(gameRules.reconnectGracePeriodSeconds()).willReturn(GRACE_SECONDS);
        var state = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                Instant.now().plusSeconds(GRACE_SECONDS));
        given(stateManager.initGracePeriod(any(), eq(GAME_ID), eq(PLAYER_ID), any()))
                .willReturn(state);
        given(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).willReturn(scheduledFuture);

        // when
        saga.start(GAME_ID, PLAYER_ID);

        // then
        then(stateManager).should().initGracePeriod(any(), eq(GAME_ID), eq(PLAYER_ID), any());
        then(lobbyRepository).should().save(any());
        then(eventPublisher).should().publish(envelopeWithPayload(PlayerDisconnected.class));
        then(taskScheduler).should().schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("handleReconnect — GRACE_PERIOD saga transitions state, restores lobby, cancels timer")
    void handleReconnect_gracePeriodActive_transitionsToReconnectedAndCancelsTimer() {
        // given
        stubGame();
        stubStartedLobby(false);
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                Instant.now().plusSeconds(GRACE_SECONDS));
        given(stateManager.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(gracePeriodState));
        given(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).willReturn(scheduledFuture);
        saga.rescheduleTimer(SAGA_ID, Instant.now().plusSeconds(GRACE_SECONDS));

        // when
        saga.handleReconnect(GAME_ID, PLAYER_ID);

        // then
        then(stateManager).should().reconnect(SAGA_ID);
        then(scheduledFuture).should().cancel(false);
        then(lobbyRepository).should().save(any());
    }

    @Test
    @DisplayName("handleReconnect — ABANDONED saga is rejected, no state change or lobby mutation")
    void handleReconnect_abandonedSaga_rejectsWithoutMutation() {
        // given
        var abandonedState = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.ABANDONED,
                Instant.now().minusSeconds(5));
        given(stateManager.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(abandonedState));

        // when
        saga.handleReconnect(GAME_ID, PLAYER_ID);

        // then
        then(stateManager).should(never()).reconnect(any());
        then(lobbyRepository).should(never()).save(any());
        then(gameRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("handleTimerExpiry — GRACE_PERIOD saga abandons player and publishes PlayerAbandoned")
    void handleTimerExpiry_gracePeriodActive_abandonsPlayerAndPublishesEvent() {
        // given
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                Instant.now().minusSeconds(1));
        given(stateManager.findBySagaId(SAGA_ID)).willReturn(Optional.of(gracePeriodState));
        stubGame();
        stubStartedLobby(false);
        given(stateManager.countActiveGracePeriodForGame(GAME_ID)).willReturn(0L);

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        then(stateManager).should().abandon(SAGA_ID);
        then(eventPublisher).should().publish(envelopeWithPayload(PlayerAbandoned.class));
    }

    @Test
    @DisplayName("handleTimerExpiry — last player abandoned triggers GameEndedAbnormally via outbox and Spring event")
    void handleTimerExpiry_lastPlayerAbandoned_publishesGameEndedAbnormally() {
        // given
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                Instant.now().minusSeconds(1));
        given(stateManager.findBySagaId(SAGA_ID)).willReturn(Optional.of(gracePeriodState));
        stubGame();
        stubStartedLobby(false);
        given(stateManager.countActiveGracePeriodForGame(GAME_ID)).willReturn(0L);

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        then(eventPublisher).should().publish(envelopeWithPayload(GameEndedAbnormally.class));
        then(applicationEventPublisher).should().publishEvent(any(GameEndedAbnormally.class));
    }

    @Test
    @DisplayName("handleTimerExpiry — idempotent when saga is no longer in GRACE_PERIOD")
    void handleTimerExpiry_notInGracePeriod_noOp() {
        // given
        given(stateManager.findBySagaId(SAGA_ID)).willReturn(Optional.empty());

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        then(stateManager).should(never()).abandon(any());
        then(eventPublisher).should(never()).publish(any());
    }
}
