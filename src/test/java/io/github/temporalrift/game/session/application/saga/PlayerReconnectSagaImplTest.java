package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@ExtendWith(MockitoExtension.class)
class PlayerReconnectSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final UUID SAGA_ID = UUID.randomUUID();
    static final int GRACE_SECONDS = 30;
    static final Instant BASE_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    static final Clock TEST_CLOCK = Clock.fixed(BASE_INSTANT, java.time.ZoneOffset.UTC);

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
    SessionGameRulesPort gameRules;

    @Mock
    PlayerReconnectTimerRegistry timerRegistry;

    PlayerReconnectSagaImpl saga;

    @BeforeEach
    void setUp() {
        saga = new PlayerReconnectSagaImpl(
                lobbyRepository,
                gameRepository,
                eventPublisher,
                applicationEventPublisher,
                stateManager,
                gameRules,
                timerRegistry,
                TEST_CLOCK);
    }

    private static EventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }

    private Game stubGame() {
        var game = new Game(GAME_ID, LOBBY_ID, List.of());
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        return game;
    }

    private Lobby stubStartedLobby(boolean playerConnected) {
        var player = new LobbyPlayer(PLAYER_ID, "Alice", null, BASE_INSTANT, playerConnected);
        var config = new LobbyConfig("ABCD", 3, 5, TEST_CLOCK);
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
                BASE_INSTANT.plusSeconds(GRACE_SECONDS));
        given(stateManager.initGracePeriod(
                        any(), eq(GAME_ID), eq(PLAYER_ID), eq(BASE_INSTANT.plusSeconds(GRACE_SECONDS))))
                .willReturn(state);

        // when
        var result = saga.start(GAME_ID, PLAYER_ID);

        // then
        then(stateManager)
                .should()
                .initGracePeriod(any(), eq(GAME_ID), eq(PLAYER_ID), eq(BASE_INSTANT.plusSeconds(GRACE_SECONDS)));
        then(lobbyRepository).should().save(any());
        then(eventPublisher).should().publish(envelopeWithPayload(PlayerDisconnected.class));
        then(timerRegistry).shouldHaveNoInteractions();
        assertThat(result.sagaId()).isNotNull();
        assertThat(result.graceExpiresAt()).isEqualTo(BASE_INSTANT.plusSeconds(GRACE_SECONDS));
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
                BASE_INSTANT.plusSeconds(GRACE_SECONDS));
        given(stateManager.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(gracePeriodState));
        given(stateManager.tryReconnect(SAGA_ID)).willReturn(true);

        // when
        saga.handleReconnect(GAME_ID, PLAYER_ID);

        // then
        then(stateManager).should().tryReconnect(SAGA_ID);
        then(timerRegistry).should().cancel(SAGA_ID);
        then(lobbyRepository).should().save(any());
    }

    @Test
    @DisplayName("handleReconnect — ABANDONED saga is rejected, no state change or lobby mutation")
    void handleReconnect_abandonedSaga_rejectsWithoutMutation() {
        // given
        var abandonedState = new PlayerReconnectSagaState(
                SAGA_ID, GAME_ID, PLAYER_ID, PlayerReconnectSagaStatus.ABANDONED, BASE_INSTANT.minusSeconds(5));
        given(stateManager.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(abandonedState));
        given(stateManager.tryReconnect(SAGA_ID)).willReturn(false);

        // when
        saga.handleReconnect(GAME_ID, PLAYER_ID);

        // then
        then(lobbyRepository).should(never()).save(any());
        then(gameRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("handleTimerExpiry — GRACE_PERIOD saga abandons player and publishes PlayerAbandoned")
    void handleTimerExpiry_gracePeriodActive_abandonsPlayerAndPublishesEvent() {
        // given
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID, GAME_ID, PLAYER_ID, PlayerReconnectSagaStatus.GRACE_PERIOD, BASE_INSTANT.minusSeconds(1));
        given(stateManager.findBySagaId(SAGA_ID)).willReturn(Optional.of(gracePeriodState));
        given(stateManager.tryAbandon(SAGA_ID)).willReturn(true);
        stubGame();
        stubStartedLobby(false);
        given(stateManager.countActiveGracePeriodForGame(GAME_ID)).willReturn(0L);

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        then(stateManager).should().tryAbandon(SAGA_ID);
        then(eventPublisher).should().publish(envelopeWithPayload(PlayerAbandoned.class));
    }

    @Test
    @DisplayName("handleTimerExpiry — last player abandoned triggers GameEndedAbnormally via outbox and Spring event")
    void handleTimerExpiry_lastPlayerAbandoned_publishesGameEndedAbnormally() {
        // given
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID, GAME_ID, PLAYER_ID, PlayerReconnectSagaStatus.GRACE_PERIOD, BASE_INSTANT.minusSeconds(1));
        given(stateManager.findBySagaId(SAGA_ID)).willReturn(Optional.of(gracePeriodState));
        given(stateManager.tryAbandon(SAGA_ID)).willReturn(true);
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
        then(stateManager).should(never()).tryAbandon(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("handleTimerExpiry — saga found but claim lost the race, no side effects run")
    void handleTimerExpiry_claimLost_noSideEffects() {
        // given — the saga row exists (e.g. already RECONNECTED, or ABANDONED by a concurrent
        // sweep/instance), but this trigger did not win the atomic transition.
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID, GAME_ID, PLAYER_ID, PlayerReconnectSagaStatus.GRACE_PERIOD, BASE_INSTANT.minusSeconds(1));
        given(stateManager.findBySagaId(SAGA_ID)).willReturn(Optional.of(gracePeriodState));
        given(stateManager.tryAbandon(SAGA_ID)).willReturn(false);

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        then(timerRegistry).should(never()).remove(any());
        then(eventPublisher).should(never()).publish(any());
        then(gameRepository).should(never()).findById(any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }
}
