package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.event.PlayerAbandoned;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.ConnectionStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.PlayerReconnectSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionFactionObjectivePort;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;
import io.github.temporalrift.game.shared.domain.event.GameEnded;
import io.github.temporalrift.game.shared.domain.event.ScoresUpdated;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
class PlayerAbandonmentEndingTest {

    private static final Instant DEADLINE = Instant.parse("2026-01-01T00:00:30Z");
    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID LOBBY_ID = UUID.randomUUID();
    private static final List<UUID> PLAYERS = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private static final List<UUID> SAGAS = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Mock
    GameRepository games;

    @Mock
    LobbyRepository lobbies;

    @Mock
    EraSagaRepository eras;

    @Mock
    PlayerReconnectSagaRepository reconnects;

    @Mock
    SessionEventPublisher events;

    @Mock
    ApplicationEventPublisher internalEvents;

    @Mock
    FinalScoreQueryPort finalScores;

    @Mock
    SessionGameRulesPort rules;

    @Mock
    PlayerReconnectTimerRegistry timers;

    @Mock
    EraSagaScoresUpdatedInboxRepository scoresInbox;

    @Mock
    SessionFactionObjectivePort objectives;

    @Mock
    TimelineCollapsePublisher collapsePublisher;

    @Mock
    Clock clock;

    private final AtomicReference<Instant> now = new AtomicReference<>(DEADLINE);
    private final Map<UUID, PlayerReconnectSagaState> grace = new HashMap<>();
    private final AtomicReference<EraSagaState> era = new AtomicReference<>();
    private Game game;
    private Lobby lobby;
    private PlayerReconnectSagaImpl reconnectSaga;
    private EraSagaAdvancer advancer;

    @BeforeEach
    void setUp() {
        game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 2, 0, GameStatus.IN_PROGRESS);
        var roster = new ArrayList<LobbyPlayer>();
        for (int i = 0; i < PLAYERS.size(); i++) {
            roster.add(new LobbyPlayer(
                    PLAYERS.get(i), "Player " + i, Faction.PROPHETS, Instant.EPOCH, ConnectionStatus.DISCONNECTED));
            grace.put(
                    SAGAS.get(i),
                    new PlayerReconnectSagaState(
                            SAGAS.get(i), GAME_ID, PLAYERS.get(i), PlayerReconnectSagaStatus.GRACE_PERIOD, DEADLINE));
        }
        lobby = Lobby.reconstitute(
                LOBBY_ID,
                GAME_ID,
                PLAYERS.getFirst(),
                roster,
                LobbyStatus.STARTED,
                new LobbyConfig("ABCD", 3, 5, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        era.set(new EraSagaState(GAME_ID, 2, EraSagaStatus.WAITING_ROUND_2, PLAYERS));
        lenient().when(clock.instant()).thenAnswer(_ -> now.get());
        lenient().when(games.findByIdWithLock(GAME_ID)).thenAnswer(_ -> Optional.of(game));
        lenient().when(lobbies.findByIdWithLock(LOBBY_ID)).thenAnswer(_ -> Optional.of(lobby));
        lenient().when(eras.findByGameIdWithLock(GAME_ID)).thenAnswer(_ -> Optional.of(era.get()));
        lenient().when(eras.save(any())).thenAnswer(inv -> {
            era.set(inv.getArgument(0));
            return era.get();
        });
        lenient()
                .when(reconnects.findBySagaId(any()))
                .thenAnswer(inv -> Optional.ofNullable(grace.get(inv.getArgument(0))));
        lenient()
                .when(reconnects.findActiveGracePeriod(any(), any()))
                .thenAnswer(inv -> grace.values().stream()
                        .filter(state -> state.gameId().equals(inv.getArgument(0))
                                && state.playerId().equals(inv.getArgument(1))
                                && state.status() == PlayerReconnectSagaStatus.GRACE_PERIOD)
                        .findFirst());
        lenient()
                .when(reconnects.findGracePeriodsDueBy(any(), any()))
                .thenAnswer(inv -> grace.values().stream()
                        .filter(state -> state.gameId().equals(inv.getArgument(0)))
                        .filter(state -> state.status() == PlayerReconnectSagaStatus.GRACE_PERIOD)
                        .filter(state -> !state.graceExpiresAt().isAfter(inv.getArgument(1)))
                        .toList());
        lenient().when(reconnects.compareAndSetStatus(any(), any(), any())).thenAnswer(inv -> {
            var state = grace.get(inv.getArgument(0));
            if (state == null || state.status() != inv.getArgument(1)) {
                return false;
            }
            grace.put(state.sagaId(), state.withStatus(inv.getArgument(2)));
            return true;
        });
        lenient().when(reconnects.save(any())).thenAnswer(inv -> {
            var state = inv.<PlayerReconnectSagaState>getArgument(0);
            grace.put(state.sagaId(), state);
            return state;
        });
        var stateManager = new PlayerReconnectSagaStateManager(reconnects);
        var abandonment = new PlayerAbandonmentProcessor(stateManager, lobbies, timers, events, clock);
        var ending = new AbandonmentEndingPublisher(events, internalEvents, clock);
        reconnectSaga = new PlayerReconnectSagaImpl(
                lobbies, games, eras, events, ending, abandonment, finalScores, stateManager, rules, timers, clock);
        advancer = new EraSagaAdvancer(
                eras,
                scoresInbox,
                games,
                lobbies,
                events,
                internalEvents,
                collapsePublisher,
                ending,
                abandonment,
                rules,
                objectives,
                clock);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void allDeadlinesPassed_neverAwardsAnOrderDependentWin(boolean staggered, boolean reverse) {
        if (staggered) {
            setDeadline(0, DEADLINE.minusMillis(2));
            setDeadline(1, DEADLINE.minusMillis(1));
        }
        fireTimers(reverse);

        assertAllAbandoned();
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_ABNORMALLY);
        assertThat(era.get().status()).isEqualTo(EraSagaStatus.COMPLETED);
        assertNoWinnerEnding();
        then(events).should(times(3)).publish(argThat(envelope -> envelope.payload() instanceof PlayerAbandoned));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void twoExpiredPlayers_theConnectedOrInGraceContenderWins(boolean inGrace, boolean reverse) {
        if (inGrace) {
            setDeadline(2, DEADLINE.plusMillis(1));
        } else {
            lobby.markPlayerReconnected(PLAYERS.get(2));
            grace.remove(SAGAS.get(2));
        }
        given(finalScores.getScores(GAME_ID))
                .willReturn(List.of(new GameEnded.PlayerScoreResult(PLAYERS.get(2), Faction.PROPHETS.name(), 9)));
        fireTimers(reverse);

        assertThat(lobby.contenders()).extracting(LobbyPlayer::playerId).containsExactly(PLAYERS.get(2));
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_WIN);
        var win = new WinConditionMet(GAME_ID, PLAYERS.get(2), Faction.PROPHETS.name(), 9, "LAST_PLAYER_STANDING");
        then(internalEvents).should().publishEvent(win);
        then(events).should(never()).publish(argThat(envelope -> envelope.payload() instanceof GameEndedAbnormally));
        then(events).should(times(2)).publish(argThat(envelope -> envelope.payload() instanceof PlayerAbandoned));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allExpiredWhileScoring_theNoWinnerEndingWaitsForScores(boolean reverse) {
        waitForScores();
        fireTimers(reverse);
        assertAllAbandoned();
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        then(internalEvents).shouldHaveNoInteractions();
        then(finalScores).shouldHaveNoInteractions();

        advancer.handleScoresUpdated(GAME_ID, scores(25));
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_ABNORMALLY);
        assertNoWinnerEnding();
        assertThat(era.get().status()).isEqualTo(EraSagaStatus.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finalContendersGraceExpiresDuringScoring_neitherNormalNorStandingWin(boolean reverse) {
        waitForScores();
        setDeadline(2, DEADLINE.plusMillis(1));
        fireTimers(reverse);
        assertThat(lobby.contenders()).hasSize(1);
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);

        now.set(DEADLINE.plusMillis(1));
        advancer.handleScoresUpdated(GAME_ID, scores(25));
        assertAllAbandoned();
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_ABNORMALLY);
        assertNoWinnerEnding();
        then(events).should(times(3)).publish(argThat(envelope -> envelope.payload() instanceof PlayerAbandoned));
    }

    @Test
    void finalContenderStillInGrace_scoredNormalVictoryKeepsPrecedence() {
        waitForScores();
        setDeadline(2, DEADLINE.plusSeconds(1));
        fireTimers(false);
        given(rules.winScoreThreshold()).willReturn(20);
        advancer.handleScoresUpdated(GAME_ID, scores(25));
        var win = new WinConditionMet(GAME_ID, PLAYERS.get(2), Faction.PROPHETS.name(), 25, "SCORE_THRESHOLD");
        then(internalEvents).should().publishEvent(win);
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_WIN);
    }

    @Test
    void callbacksAfterTerminalState_doNotAbandonAnyone() {
        game.end();
        fireTimers(false);
        assertThat(lobby.contenders()).hasSize(3);
        assertThat(grace.values()).allMatch(state -> state.status() == PlayerReconnectSagaStatus.COMPLETED);
        then(events).shouldHaveNoInteractions();
        then(reconnects)
                .should(never())
                .compareAndSetStatus(
                        any(), any(), org.mockito.ArgumentMatchers.eq(PlayerReconnectSagaStatus.ABANDONED));
    }

    @Test
    void earlyTimer_doesNotAbandonOrEndGame() {
        now.set(DEADLINE.minusMillis(1));
        fireTimers(false);
        assertThat(lobby.contenders()).hasSize(3);
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void reconnectAtDeadline_isRejectedEvenBeforeExpiryCallback() {
        reconnectSaga.handleReconnect(GAME_ID, PLAYERS.getFirst());
        assertThat(lobby.currentPlayers().getFirst().connection()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(grace.get(SAGAS.getFirst()).status()).isEqualTo(PlayerReconnectSagaStatus.GRACE_PERIOD);
        then(reconnects).should(never()).compareAndSetStatus(any(), any(), any());
        then(lobbies).should(never()).save(any());
    }

    @Test
    void validReconnect_locksBeforeClaimingAndCannotBeAbandonedByLaterCallback() {
        now.set(DEADLINE.minusMillis(1));
        reconnectSaga.handleReconnect(GAME_ID, PLAYERS.getFirst());
        var order = inOrder(reconnects, eras, games, lobbies);
        order.verify(reconnects).findActiveGracePeriod(GAME_ID, PLAYERS.getFirst());
        order.verify(eras).findByGameIdWithLock(GAME_ID);
        order.verify(games).findByIdWithLock(GAME_ID);
        order.verify(lobbies).findByIdWithLock(LOBBY_ID);
        order.verify(reconnects).findActiveGracePeriod(GAME_ID, PLAYERS.getFirst());
        order.verify(reconnects)
                .compareAndSetStatus(
                        SAGAS.getFirst(),
                        PlayerReconnectSagaStatus.GRACE_PERIOD,
                        PlayerReconnectSagaStatus.RECONNECTED);
        now.set(DEADLINE);
        reconnectSaga.handleTimerExpiry(SAGAS.getFirst());
        assertThat(lobby.currentPlayers().getFirst().connection()).isEqualTo(ConnectionStatus.CONNECTED);
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void duplicateDisconnect_reusesItsActivePeriodWithoutExtendingGrace() {
        var started = reconnectSaga.start(GAME_ID, PLAYERS.getFirst()).orElseThrow();
        assertThat(started.sagaId()).isEqualTo(SAGAS.getFirst());
        assertThat(started.graceExpiresAt()).isEqualTo(DEADLINE);
        then(reconnects).should(never()).save(any());
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void disconnectAfterTerminalState_doesNotCreateANewPeriod() {
        game.end();
        assertThat(reconnectSaga.start(GAME_ID, PLAYERS.getFirst())).isEmpty();
        then(reconnects).should(never()).save(any());
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void secondDisconnectCycle_usesTheNewPeriodAndRetainsHistory() {
        now.set(DEADLINE.minusSeconds(1));
        reconnectSaga.handleReconnect(GAME_ID, PLAYERS.getFirst());
        given(rules.reconnectGracePeriodSeconds()).willReturn(30);
        var started = reconnectSaga.start(GAME_ID, PLAYERS.getFirst()).orElseThrow();
        reconnectSaga.handleReconnect(GAME_ID, PLAYERS.getFirst());
        assertThat(started.sagaId()).isNotEqualTo(SAGAS.getFirst());
        assertThat(grace.get(SAGAS.getFirst()).status()).isEqualTo(PlayerReconnectSagaStatus.RECONNECTED);
        assertThat(grace.get(started.sagaId()).status()).isEqualTo(PlayerReconnectSagaStatus.RECONNECTED);
        assertThat(lobby.currentPlayers().getFirst().connection()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    private void setDeadline(int player, Instant deadline) {
        var state = grace.get(SAGAS.get(player));
        grace.put(
                state.sagaId(),
                new PlayerReconnectSagaState(state.sagaId(), GAME_ID, state.playerId(), state.status(), deadline));
    }

    private void fireTimers(boolean reverse) {
        for (int i = 0; i < SAGAS.size(); i++) {
            reconnectSaga.handleTimerExpiry(SAGAS.get(reverse ? 2 - i : i));
        }
    }

    private void waitForScores() {
        era.set(era.get().withStatus(EraSagaStatus.WAITING_SCORES));
    }

    private ScoresUpdated scores(int total) {
        return new ScoresUpdated(
                GAME_ID,
                2,
                PLAYERS.stream()
                        .map(id -> new ScoresUpdated.ScoreUpdate(id, Faction.PROPHETS, 5, "bonus", total))
                        .toList());
    }

    private void assertAllAbandoned() {
        assertThat(lobby.contenders()).isEmpty();
        assertThat(grace.values()).allMatch(state -> state.status() == PlayerReconnectSagaStatus.ABANDONED);
    }

    private void assertNoWinnerEnding() {
        var ending = new GameEndedAbnormally(GAME_ID, GameEndedAbnormally.Reason.ALL_PLAYERS_ABANDONED);
        then(internalEvents).should().publishEvent(ending);
        then(events).should().publish(argThat(envelope -> ending.equals(envelope.payload())));
        then(events).should(never()).publish(argThat(envelope -> envelope.payload() instanceof WinConditionMet));
    }
}
