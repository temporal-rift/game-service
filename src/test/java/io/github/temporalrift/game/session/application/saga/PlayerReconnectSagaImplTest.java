package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
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

import io.github.temporalrift.game.session.domain.event.PlayerAbandoned;
import io.github.temporalrift.game.session.domain.event.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.ConnectionStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;
import io.github.temporalrift.game.shared.domain.event.GameEnded;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
class PlayerReconnectSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final UUID OTHER_1 = UUID.randomUUID();
    static final UUID OTHER_2 = UUID.randomUUID();
    static final UUID SAGA_ID = UUID.randomUUID();
    static final int GRACE_SECONDS = 30;
    static final Instant BASE_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    static final Clock TEST_CLOCK = Clock.fixed(BASE_INSTANT, java.time.ZoneOffset.UTC);

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    GameRepository gameRepository;

    @Mock
    EraSagaRepository eraSagaRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    FinalScoreQueryPort finalScoreQueryPort;

    @Mock
    SessionEventPublisher eventPublisher;

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
                eraSagaRepository,
                eventPublisher,
                new AbandonmentEndingPublisher(eventPublisher, applicationEventPublisher, TEST_CLOCK),
                new PlayerAbandonmentProcessor(
                        stateManager, lobbyRepository, timerRegistry, eventPublisher, TEST_CLOCK),
                finalScoreQueryPort,
                stateManager,
                gameRules,
                timerRegistry,
                TEST_CLOCK);
    }

    private static DomainEventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }

    private Game stubGame() {
        var game = new Game(GAME_ID, LOBBY_ID, List.of());
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        return game;
    }

    private Lobby stubStartedLobby(boolean playerConnected) {
        var lobby = startedLobby(playerConnected);
        given(lobbyRepository.findByIdWithLock(LOBBY_ID)).willReturn(Optional.of(lobby));
        return lobby;
    }

    private Lobby startedLobby(boolean playerConnected) {
        var player = new LobbyPlayer(
                PLAYER_ID,
                "Alice",
                null,
                BASE_INSTANT,
                playerConnected ? ConnectionStatus.CONNECTED : ConnectionStatus.DISCONNECTED);
        var config = new LobbyConfig("ABCD", 3, 5, TEST_CLOCK);
        return Lobby.reconstitute(LOBBY_ID, GAME_ID, PLAYER_ID, List.of(player), LobbyStatus.STARTED, config);
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
        var result = saga.start(GAME_ID, PLAYER_ID).orElseThrow();

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
        stubGameWithLock(GameStatus.IN_PROGRESS);
        stubStartedLobby(false);
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID,
                GAME_ID,
                PLAYER_ID,
                PlayerReconnectSagaStatus.GRACE_PERIOD,
                BASE_INSTANT.plusSeconds(GRACE_SECONDS));
        given(stateManager.findActiveGracePeriod(GAME_ID, PLAYER_ID)).willReturn(Optional.of(gracePeriodState));
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
        given(stateManager.findActiveGracePeriod(GAME_ID, PLAYER_ID)).willReturn(Optional.of(abandonedState));

        // when
        saga.handleReconnect(GAME_ID, PLAYER_ID);

        // then
        then(lobbyRepository).should(never()).save(any());
        then(gameRepository).should(never()).findById(any());
    }

    private void givenExpiredGracePeriod() {
        var gracePeriodState = new PlayerReconnectSagaState(
                SAGA_ID, GAME_ID, PLAYER_ID, PlayerReconnectSagaStatus.GRACE_PERIOD, BASE_INSTANT.minusSeconds(1));
        given(stateManager.findBySagaId(SAGA_ID)).willReturn(Optional.of(gracePeriodState));
        lenient().when(stateManager.tryAbandon(SAGA_ID)).thenReturn(true);
        lenient()
                .when(stateManager.findGracePeriodsDueBy(GAME_ID, BASE_INSTANT))
                .thenReturn(List.of(gracePeriodState));
    }

    private Lobby stubThreePlayerLobbyWithLock(UUID... alreadyAbandoned) {
        var players = List.of(
                new LobbyPlayer(PLAYER_ID, "Alice", Faction.PROPHETS, BASE_INSTANT, ConnectionStatus.DISCONNECTED),
                new LobbyPlayer(OTHER_1, "Bob", Faction.WEAVERS, BASE_INSTANT, ConnectionStatus.CONNECTED),
                new LobbyPlayer(OTHER_2, "Carol", Faction.ERASERS, BASE_INSTANT, ConnectionStatus.DISCONNECTED));
        var lobby = Lobby.reconstitute(
                LOBBY_ID, GAME_ID, PLAYER_ID, players, LobbyStatus.STARTED, new LobbyConfig("ABCD", 3, 5, TEST_CLOCK));
        for (var playerId : alreadyAbandoned) {
            lobby.markPlayerAbandoned(playerId);
        }
        given(lobbyRepository.findByIdWithLock(LOBBY_ID)).willReturn(Optional.of(lobby));
        return lobby;
    }

    private Game stubGameWithLock(GameStatus status) {
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 2, 0, status);
        given(gameRepository.findByIdWithLock(GAME_ID)).willReturn(Optional.of(game));
        return game;
    }

    @Test
    @DisplayName(
            "handleTimerExpiry — abandons the player, publishes PlayerAbandoned, game continues with two contenders")
    void handleTimerExpiry_twoContendersRemain_abandonsWithoutEnding() {
        // given
        givenExpiredGracePeriod();
        var game = stubGameWithLock(GameStatus.IN_PROGRESS);
        var lobby = stubThreePlayerLobbyWithLock();

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        assertThat(lobby.isAbandoned(PLAYER_ID)).isTrue();
        then(lobbyRepository).should().save(lobby);
        then(eventPublisher).should().publish(envelopeWithPayload(PlayerAbandoned.class));
        then(eventPublisher).should(never()).publish(envelopeWithPayload(WinConditionMet.class));
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("handleTimerExpiry — one contender left wins by LAST_PLAYER_STANDING and the era saga completes")
    void handleTimerExpiry_oneContenderLeft_endsByLastPlayerStanding() {
        // given — OTHER_2 already abandoned; OTHER_1 is the last player standing
        givenExpiredGracePeriod();
        var game = stubGameWithLock(GameStatus.IN_PROGRESS);
        stubThreePlayerLobbyWithLock(OTHER_2);
        var eraSaga = new EraSagaState(GAME_ID, 2, EraSagaStatus.WAITING_ROUND_2, List.of(PLAYER_ID, OTHER_1, OTHER_2));
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(eraSaga));
        given(finalScoreQueryPort.getScores(GAME_ID))
                .willReturn(List.of(
                        new GameEnded.PlayerScoreResult(PLAYER_ID, Faction.PROPHETS.name(), 19),
                        new GameEnded.PlayerScoreResult(OTHER_1, Faction.WEAVERS.name(), 7),
                        new GameEnded.PlayerScoreResult(OTHER_2, Faction.ERASERS.name(), 12)));

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_WIN);
        then(gameRepository).should().save(game);
        then(eraSagaRepository).should().save(eraSaga.withStatus(EraSagaStatus.COMPLETED));
        var expected = new WinConditionMet(GAME_ID, OTHER_1, Faction.WEAVERS.name(), 7, "LAST_PLAYER_STANDING");
        then(eventPublisher).should().publish(argThat(envelope -> expected.equals(envelope.payload())));
        then(applicationEventPublisher).should().publishEvent(expected);
    }

    @Test
    @DisplayName("handleTimerExpiry — one contender left while the era's scoring is in flight defers the ending")
    void handleTimerExpiry_oneContenderLeftDuringScoring_defersToEraBoundary() {
        // given
        givenExpiredGracePeriod();
        var game = stubGameWithLock(GameStatus.IN_PROGRESS);
        var lobby = stubThreePlayerLobbyWithLock(OTHER_2);
        var eraSaga = new EraSagaState(GAME_ID, 2, EraSagaStatus.WAITING_SCORES, List.of(PLAYER_ID, OTHER_1, OTHER_2));
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(eraSaga));

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then — the forfeit is recorded; the era-end decision ends the game once scoring commits
        assertThat(lobby.isAbandoned(PLAYER_ID)).isTrue();
        then(eventPublisher).should().publish(envelopeWithPayload(PlayerAbandoned.class));
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        then(gameRepository).should(never()).save(any());
        then(eraSagaRepository).should(never()).save(any());
        then(finalScoreQueryPort).shouldHaveNoInteractions();
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handleTimerExpiry — the remaining player still in grace wins by LAST_PLAYER_STANDING")
    void handleTimerExpiry_remainingPlayerInGrace_winsByLastPlayerStanding() {
        // given — OTHER_1 already abandoned; OTHER_2 is disconnected but has not forfeited
        givenExpiredGracePeriod();
        stubGameWithLock(GameStatus.IN_PROGRESS);
        stubThreePlayerLobbyWithLock(OTHER_1);
        given(finalScoreQueryPort.getScores(GAME_ID)).willReturn(List.of());

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        var expected = new WinConditionMet(GAME_ID, OTHER_2, Faction.ERASERS.name(), 0, "LAST_PLAYER_STANDING");
        then(applicationEventPublisher).should().publishEvent(expected);
    }

    @Test
    @DisplayName("handleTimerExpiry — after the game ended, the expiry records and publishes nothing")
    void handleTimerExpiry_gameAlreadyEnded_recordsNothing() {
        // given — e.g. this player won by last player standing while still inside their own grace period
        givenExpiredGracePeriod();
        var game = stubGameWithLock(GameStatus.ENDED_BY_WIN);

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        then(stateManager).should(never()).tryAbandon(any());
        then(lobbyRepository).shouldHaveNoInteractions();
        then(gameRepository).should(never()).save(any());
        then(eventPublisher).shouldHaveNoInteractions();
        then(applicationEventPublisher).shouldHaveNoInteractions();
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_WIN);
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
        given(stateManager.findGracePeriodsDueBy(GAME_ID, BASE_INSTANT)).willReturn(List.of(gracePeriodState));
        given(stateManager.tryAbandon(SAGA_ID)).willReturn(false);
        stubGameWithLock(GameStatus.IN_PROGRESS);
        stubThreePlayerLobbyWithLock();

        // when
        saga.handleTimerExpiry(SAGA_ID);

        // then
        then(timerRegistry).should(never()).remove(any());
        then(eventPublisher).should(never()).publish(any());
        then(gameRepository).should(never()).findById(any());
    }
}
