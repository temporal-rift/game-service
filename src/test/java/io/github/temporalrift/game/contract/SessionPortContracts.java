package io.github.temporalrift.game.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.session.domain.lobby.ConnectionStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.EndGameSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.session.domain.port.out.ForesightRevealRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.PlayerReconnectSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaState;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaStatus;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;
import io.github.temporalrift.game.shared.domain.event.ScoresUpdated;
import io.github.temporalrift.game.shared.domain.model.CarryOverState;
import io.github.temporalrift.game.shared.domain.model.Faction;

/** The session module's persisted driven ports, one contract each. */
public final class SessionPortContracts {

    private SessionPortContracts() {}

    public interface LobbyPortContract extends PortContract {

        @Test
        default void lobby_roundTripsAllFields() {
            var repository = port(LobbyRepository.class);
            var clock = port(Clock.class);
            var id = UUID.randomUUID();
            var gameId = UUID.randomUUID();
            var hostId = UUID.randomUUID();
            var players = new ArrayList<LobbyPlayer>();
            players.add(new LobbyPlayer(
                    hostId,
                    "Alice",
                    Faction.ERASERS,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    ConnectionStatus.CONNECTED));
            players.add(new LobbyPlayer(
                    UUID.randomUUID(),
                    "Bob",
                    Faction.PROPHETS,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    ConnectionStatus.CONNECTED));

            repository.save(new Lobby(id, gameId, hostId, players, new LobbyConfig("SAVE" + code(), 2, 5, clock)));

            var loaded = repository.findById(id).orElseThrow();
            assertThat(loaded.gameId()).isEqualTo(gameId);
            assertThat(loaded.hostPlayerId()).isEqualTo(hostId);
            assertThat(loaded.status()).isEqualTo(LobbyStatus.WAITING);
            assertThat(loaded.currentPlayers()).hasSize(2);
            assertThat(loaded.currentPlayers()).anySatisfy(player -> {
                assertThat(player.playerId()).isEqualTo(hostId);
                assertThat(player.playerName()).isEqualTo("Alice");
                assertThat(player.faction()).isEqualTo(Faction.ERASERS);
            });
        }

        @Test
        default void lobby_findByJoinCode_findsOnlyTheMatchingLobby() {
            var repository = port(LobbyRepository.class);
            var clock = port(Clock.class);
            var id = UUID.randomUUID();
            var joinCode = "FIND" + code();
            repository.save(new Lobby(
                    id,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new ArrayList<>(),
                    new LobbyConfig(joinCode, 2, 5, clock)));

            assertThat(repository.findByJoinCode(joinCode))
                    .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(id));
            assertThat(repository.findByJoinCode("NOPE" + code())).isEmpty();
        }

        @Test
        default void lobby_readCopyIsIndependentOfStoredState() {
            var repository = port(LobbyRepository.class);
            var clock = port(Clock.class);
            var id = UUID.randomUUID();
            var hostId = UUID.randomUUID();
            repository.save(new Lobby(
                    id,
                    UUID.randomUUID(),
                    hostId,
                    new ArrayList<>(List.of(new LobbyPlayer(
                            hostId, "Alice", null, Instant.parse("2026-01-01T00:00:00Z"), ConnectionStatus.CONNECTED))),
                    new LobbyConfig("COPY" + code(), 2, 5, clock)));

            var first = repository.findById(id).orElseThrow();
            first.join(UUID.randomUUID(), "Eve");

            assertThat(repository.findById(id).orElseThrow().currentPlayers()).hasSize(1);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void lobby_lockMakesASecondTransactionWait() {
            var repository = port(LobbyRepository.class);
            var clock = port(Clock.class);
            var id = UUID.randomUUID();
            inNewTransaction(() -> repository.save(new Lobby(
                    id,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new ArrayList<>(),
                    new LobbyConfig("LOCK" + code(), 2, 5, clock))));

            assertSecondLockerWaitsForFirst(() -> repository.findByIdWithLock(id));
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void lobby_duplicateJoinCodeIsRejected() {
            var repository = port(LobbyRepository.class);
            var clock = port(Clock.class);
            var joinCode = "DUPL" + code();
            inNewTransaction(() -> repository.save(new Lobby(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new ArrayList<>(),
                    new LobbyConfig(joinCode, 2, 5, clock))));

            assertThatThrownBy(() -> inNewTransaction(() -> repository.save(new Lobby(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            new ArrayList<>(),
                            new LobbyConfig(joinCode, 2, 5, clock)))))
                    .isInstanceOf(RuntimeException.class);
        }

        private static String code() {
            return UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        }
    }

    public interface GamePortContract extends PortContract {

        @Test
        default void game_roundTripsDeckStatusAndEraProgress() {
            var repository = port(GameRepository.class);
            var id = UUID.randomUUID();
            var game = new Game(id, UUID.randomUUID(), eventIds(15));
            game.startEra(0, 3);

            repository.save(game);

            var loaded = repository.findById(id).orElseThrow();
            assertThat(loaded.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(loaded.eraCounter()).isEqualTo(1);
            assertThat(loaded.eventDeck()).containsExactlyElementsOf(game.eventDeck());
        }

        @Test
        default void game_roundTripsCascadedEventsAndCollapsePending() {
            var repository = port(GameRepository.class);
            var id = UUID.randomUUID();
            var game = new Game(id, UUID.randomUUID(), eventIds(15));
            var reCascading = UUID.randomUUID();
            var third = UUID.randomUUID();
            game.recordCascadedParadoxesInRevealOrder(List.of(reCascading), 3);
            repository.save(game);
            game.recordCascadedParadoxesInRevealOrder(List.of(reCascading, UUID.randomUUID(), third), 3);
            repository.save(game);

            var loaded = repository.findById(id).orElseThrow();

            assertThat(loaded.cascadedParadoxCounter()).isEqualTo(3);
            assertThat(loaded.cascadedEventIds()).hasSize(3).contains(reCascading, third);
            assertThat(loaded.collapsePending()).isTrue();
        }

        @Test
        default void game_preservesPendingCarryOverEventsInOrder() {
            var repository = port(GameRepository.class);
            var id = UUID.randomUUID();
            var first = new PendingCarryOverEvent(UUID.randomUUID(), CarryOverState.STALLED);
            var second = new PendingCarryOverEvent(UUID.randomUUID(), CarryOverState.CASCADED);
            var game = new Game(id, UUID.randomUUID(), new ArrayList<>());
            game.recordPendingCarryOverEvents(List.of(first, second));

            repository.save(game);

            assertThat(repository.findById(id).orElseThrow().pendingCarryOverEvents())
                    .containsExactly(first, second);
        }

        @Test
        default void game_unknownIdIsEmpty() {
            assertThat(port(GameRepository.class).findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        default void game_changingAReadCopyDoesNotChangeStoredState() {
            var repository = port(GameRepository.class);
            var id = UUID.randomUUID();
            repository.save(new Game(id, UUID.randomUUID(), eventIds(15)));

            var copy = repository.findById(id).orElseThrow();
            copy.startEra(0, 3);

            assertThat(repository.findById(id).orElseThrow().eraCounter()).isZero();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void game_rollbackLeavesNothingBehind() {
            var repository = port(GameRepository.class);
            var id = UUID.randomUUID();

            assertThatThrownBy(() -> inNewTransaction(() -> {
                        repository.save(new Game(id, UUID.randomUUID(), eventIds(15)));
                        throw new IllegalStateException("boom");
                    }))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(inNewTransaction(() -> repository.findById(id))).isEmpty();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void game_uncommittedSaveIsInvisibleToAnotherTransaction() {
            var repository = port(GameRepository.class);
            var id = UUID.randomUUID();
            var saved = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var writer = java.util.concurrent.CompletableFuture.runAsync(() -> inNewTransaction(() -> {
                repository.save(new Game(id, UUID.randomUUID(), eventIds(15)));
                saved.countDown();
                try {
                    release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            try {
                assertThat(saved.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();
                assertThat(inNewTransaction(() -> repository.findById(id))).isEmpty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                release.countDown();
                writer.join();
            }
            assertThat(inNewTransaction(() -> repository.findById(id))).isPresent();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void game_lockMakesASecondTransactionWait() {
            var repository = port(GameRepository.class);
            var id = UUID.randomUUID();
            inNewTransaction(() -> repository.save(new Game(id, UUID.randomUUID(), eventIds(15))));

            assertSecondLockerWaitsForFirst(() -> repository.findByIdWithLock(id));
        }

        private static ArrayList<UUID> eventIds(int count) {
            return IntStream.range(0, count)
                    .mapToObj(ignored -> UUID.randomUUID())
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    public interface StartGameSagaPortContract extends PortContract {

        @Test
        default void startGameSaga_roundTripsFactionAssignments() {
            var repository = port(StartGameSagaRepository.class);
            var gameId = UUID.randomUUID();
            var saga = new StartGameSagaState(
                    UUID.randomUUID(),
                    gameId,
                    UUID.randomUUID(),
                    StartGameSagaStatus.RUNNING,
                    List.of(new FactionAssignment(UUID.randomUUID(), Faction.WEAVERS)));

            repository.save(saga);

            assertThat(repository.findByGameId(gameId)).contains(saga);
            assertThat(repository.findByGameId(UUID.randomUUID())).isEmpty();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void startGameSaga_lockMakesASecondTransactionWait() {
            var repository = port(StartGameSagaRepository.class);
            var gameId = UUID.randomUUID();
            inNewTransaction(() -> repository.save(new StartGameSagaState(
                    UUID.randomUUID(), gameId, UUID.randomUUID(), StartGameSagaStatus.RUNNING, List.of())));

            assertSecondLockerWaitsForFirst(() -> repository.findByGameIdWithLock(gameId));
        }
    }

    public interface EraSagaPortContract extends PortContract {

        @Test
        default void eraSaga_roundTripsStateAndPlayers() {
            var repository = port(EraSagaRepository.class);
            var gameId = UUID.randomUUID();
            var players = List.of(UUID.randomUUID(), UUID.randomUUID());
            repository.save(new EraSagaState(gameId, 2, EraSagaStatus.WAITING_SCORES, players));

            var loaded = repository.findByGameId(gameId).orElseThrow();

            assertThat(loaded.eraNumber()).isEqualTo(2);
            assertThat(loaded.status()).isEqualTo(EraSagaStatus.WAITING_SCORES);
            assertThat(loaded.playerIds()).containsExactlyElementsOf(players);
        }

        @Test
        default void eraSaga_saveReplacesTheGamesState() {
            var repository = port(EraSagaRepository.class);
            var gameId = UUID.randomUUID();
            repository.save(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_SCORES, List.of()));
            repository.save(new EraSagaState(gameId, 2, EraSagaStatus.COMPLETED, List.of()));

            assertThat(repository.findByGameId(gameId).orElseThrow().eraNumber())
                    .isEqualTo(2);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void eraSaga_lockMakesASecondTransactionWait() {
            var repository = port(EraSagaRepository.class);
            var gameId = UUID.randomUUID();
            inNewTransaction(
                    () -> repository.save(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_SCORES, List.of())));

            assertSecondLockerWaitsForFirst(() -> repository.findByGameIdWithLock(gameId));
        }
    }

    public interface EndGameSagaPortContract extends PortContract {

        @Test
        default void endGameSaga_claimIfAbsentClaimsOnlyOnce() {
            var repository = port(EndGameSagaRepository.class);
            var gameId = UUID.randomUUID();
            var players = List.of(UUID.randomUUID());
            var claim =
                    new EndGameSagaState(gameId, EndGameTrigger.TIMELINE_COLLAPSED, EndGameSagaStatus.RUNNING, players);
            var duplicate =
                    new EndGameSagaState(gameId, EndGameTrigger.DECK_EXHAUSTED, EndGameSagaStatus.COMPLETED, List.of());

            assertThat(repository.claimIfAbsent(claim)).isTrue();
            assertThat(repository.claimIfAbsent(duplicate)).isFalse();
            assertThat(repository.findByGameIdWithLock(gameId)).contains(claim);
        }

        @Test
        default void endGameSaga_saveUpdatesTheClaimedState() {
            var repository = port(EndGameSagaRepository.class);
            var gameId = UUID.randomUUID();
            var claim = new EndGameSagaState(
                    gameId, EndGameTrigger.WIN_CONDITION_MET, EndGameSagaStatus.RUNNING, List.of());
            repository.claimIfAbsent(claim);

            repository.save(claim.withStatus(EndGameSagaStatus.COMPLETED));

            assertThat(repository.findByGameIdWithLock(gameId).orElseThrow().status())
                    .isEqualTo(EndGameSagaStatus.COMPLETED);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void endGameSaga_rolledBackClaimDoesNotBlockALaterClaim() {
            var repository = port(EndGameSagaRepository.class);
            var gameId = UUID.randomUUID();
            var claim = new EndGameSagaState(
                    gameId, EndGameTrigger.WIN_CONDITION_MET, EndGameSagaStatus.RUNNING, List.of());
            assertThatThrownBy(() -> inNewTransaction(() -> {
                        repository.claimIfAbsent(claim);
                        throw new IllegalStateException("boom");
                    }))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(inNewTransaction(() -> repository.claimIfAbsent(claim))).isTrue();
        }
    }

    public interface PlayerReconnectSagaPortContract extends PortContract {

        @Test
        default void reconnectSaga_gameDueQueryIncludesTheBoundaryAndExcludesTheRest() {
            var repository = port(PlayerReconnectSagaRepository.class);
            var gameId = UUID.randomUUID();
            var decisionAt = Instant.parse("2026-01-01T00:00:30Z");
            var past = save(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt.minusMillis(1));
            var exact = save(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt);
            save(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt.plusMillis(1));
            save(gameId, PlayerReconnectSagaStatus.RECONNECTED, decisionAt);
            save(UUID.randomUUID(), PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt);

            assertThat(repository.findGracePeriodsDueBy(gameId, decisionAt)).containsExactlyInAnyOrder(past, exact);
        }

        @Test
        default void reconnectSaga_compareAndSetStatusSucceedsOnlyFromTheExpectedStatus() {
            var repository = port(PlayerReconnectSagaRepository.class);
            var gameId = UUID.randomUUID();
            var saga = save(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD, Instant.parse("2026-01-01T00:00:30Z"));

            assertThat(repository.compareAndSetStatus(
                            saga.sagaId(), PlayerReconnectSagaStatus.GRACE_PERIOD, PlayerReconnectSagaStatus.ABANDONED))
                    .isTrue();
            assertThat(repository.compareAndSetStatus(
                            saga.sagaId(), PlayerReconnectSagaStatus.GRACE_PERIOD, PlayerReconnectSagaStatus.ABANDONED))
                    .isFalse();
            assertThat(repository.findBySagaId(saga.sagaId()).orElseThrow().status())
                    .isEqualTo(PlayerReconnectSagaStatus.ABANDONED);
        }

        @Test
        default void reconnectSaga_activeLookupExcludesFinishedPeriods() {
            var repository = port(PlayerReconnectSagaRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var deadline = Instant.parse("2026-01-01T00:00:30Z");
            repository.save(new PlayerReconnectSagaState(
                    UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.RECONNECTED, deadline));
            var active = repository.save(new PlayerReconnectSagaState(
                    UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.GRACE_PERIOD, deadline));

            assertThat(repository.findActiveGracePeriod(gameId, playerId)).contains(active);
            repository.compareAndSetStatus(
                    active.sagaId(), PlayerReconnectSagaStatus.GRACE_PERIOD, PlayerReconnectSagaStatus.RECONNECTED);
            assertThat(repository.findActiveGracePeriod(gameId, playerId)).isEmpty();
        }

        @Test
        default void reconnectSaga_statusDueByQueryIsOrderedByDeadline() {
            var repository = port(PlayerReconnectSagaRepository.class);
            var gameId = UUID.randomUUID();
            var later = save(gameId, PlayerReconnectSagaStatus.ABANDONED, Instant.parse("2026-01-01T00:00:20Z"));
            var earlier = save(gameId, PlayerReconnectSagaStatus.ABANDONED, Instant.parse("2026-01-01T00:00:10Z"));

            var due = repository.findByStatusDueBy(
                    PlayerReconnectSagaStatus.ABANDONED, Instant.parse("2026-01-01T00:00:30Z"));

            assertThat(due.stream().filter(saga -> saga.gameId().equals(gameId)))
                    .containsExactly(earlier, later);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void reconnectSaga_secondActivePeriodForTheSamePlayerIsRejected() {
            var repository = port(PlayerReconnectSagaRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var deadline = Instant.parse("2026-01-01T00:00:30Z");
            inNewTransaction(() -> repository.save(new PlayerReconnectSagaState(
                    UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.GRACE_PERIOD, deadline)));

            assertThatThrownBy(() -> inNewTransaction(() -> repository.save(new PlayerReconnectSagaState(
                            UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.GRACE_PERIOD, deadline))))
                    .isInstanceOf(RuntimeException.class);
        }

        private PlayerReconnectSagaState save(UUID gameId, PlayerReconnectSagaStatus status, Instant deadline) {
            return port(PlayerReconnectSagaRepository.class)
                    .save(new PlayerReconnectSagaState(UUID.randomUUID(), gameId, UUID.randomUUID(), status, deadline));
        }
    }

    public interface EraSagaScoresUpdatedInboxPortContract extends PortContract {

        @Test
        default void scoresInbox_returnsRecordedErasWhoseSagaIsStillWaiting() {
            var sagas = port(EraSagaRepository.class);
            var inbox = port(EraSagaScoresUpdatedInboxRepository.class);
            var gameId = UUID.randomUUID();
            sagas.save(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));
            var update = scoresUpdated(gameId, 1);

            inbox.save(update);

            assertThat(inbox.findRecordedButNotAdvanced()).contains(update);
        }

        @Test
        default void scoresInbox_ignoresAdvancedSagasAndStaleErasAndMissingRecords() {
            var sagas = port(EraSagaRepository.class);
            var inbox = port(EraSagaScoresUpdatedInboxRepository.class);
            var advanced = UUID.randomUUID();
            var stale = UUID.randomUUID();
            var unrecorded = UUID.randomUUID();
            sagas.save(new EraSagaState(advanced, 1, EraSagaStatus.COMPLETED, List.of(UUID.randomUUID())));
            inbox.save(scoresUpdated(advanced, 1));
            sagas.save(new EraSagaState(stale, 2, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));
            inbox.save(scoresUpdated(stale, 1));
            sagas.save(new EraSagaState(unrecorded, 1, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));

            assertThat(inbox.findRecordedButNotAdvanced())
                    .noneMatch(update -> List.of(advanced, stale, unrecorded).contains(update.gameId()));
        }

        @Test
        default void scoresInbox_duplicateDeliveryIsIdempotent() {
            var sagas = port(EraSagaRepository.class);
            var inbox = port(EraSagaScoresUpdatedInboxRepository.class);
            var gameId = UUID.randomUUID();
            sagas.save(new EraSagaState(gameId, 1, EraSagaStatus.WAITING_SCORES, List.of(UUID.randomUUID())));
            var update = scoresUpdated(gameId, 1);

            inbox.save(update);
            inbox.save(update);

            assertThat(inbox.findRecordedButNotAdvanced().stream()
                            .filter(found -> found.gameId().equals(gameId)))
                    .hasSize(1);
        }

        private static ScoresUpdated scoresUpdated(UUID gameId, int eraNumber) {
            return new ScoresUpdated(
                    gameId,
                    eraNumber,
                    List.of(new ScoresUpdated.ScoreUpdate(UUID.randomUUID(), Faction.PROPHETS, 2, "bonus", 10)));
        }
    }

    public interface ForesightRevealPortContract extends PortContract {

        @Test
        default void foresightReveal_roundTripsThePreviewAndKeepsTheFirstWrite() {
            var reveals = port(ForesightRevealRepository.class);
            var gameId = UUID.randomUUID();
            var viewer = UUID.randomUUID();
            var catalogEventIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            assertThat(reveals.saveIfAbsent(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null)))
                    .isTrue();
            assertThat(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                    .contains(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null));

            assertThat(reveals.saveIfAbsent(
                            new ForesightReveal(gameId, 2, viewer, 3, List.of(UUID.randomUUID()), "final-era")))
                    .isFalse();
            assertThat(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                    .contains(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null));
        }

        @Test
        default void foresightReveal_unknownViewerIsEmpty() {
            assertThat(port(ForesightRevealRepository.class)
                            .findByGameIdAndEraNumberAndPlayerId(UUID.randomUUID(), 2, UUID.randomUUID()))
                    .isEmpty();
        }
    }
}
