package io.github.temporalrift.game.session.domain.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.session.HostTransferred;
import io.github.temporalrift.events.session.LobbyClosed;
import io.github.temporalrift.events.session.LobbyCreated;
import io.github.temporalrift.events.session.PlayerJoinedLobby;
import io.github.temporalrift.events.session.PlayerLeftLobby;
import io.github.temporalrift.events.shared.Faction;

class LobbyTest {

    static final String JOIN_CODE = "X7K2P9";
    static final int MIN_PLAYERS = 3;
    static final int MAX_PLAYERS = 5;
    static final String[] PLAYER_NAMES = {"Alice", "Bob", "Charlie", "Diana", "Eve"};
    static final Instant FIXED_NOW = Instant.parse("2025-01-01T12:00:00Z");
    static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    int playerIndex = 0;

    Lobby emptyLobby() {
        return new Lobby(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                JOIN_CODE,
                new ArrayList<>(),
                MIN_PLAYERS,
                MAX_PLAYERS,
                FIXED_CLOCK);
    }

    LobbyPlayer player(Faction faction) {
        return new LobbyPlayer(
                UUID.randomUUID(), PLAYER_NAMES[playerIndex++ % PLAYER_NAMES.length], faction, null, true);
    }

    LobbyPlayer player() {
        return player(null);
    }

    LobbyPlayer disconnectedPlayer() {
        return new LobbyPlayer(UUID.randomUUID(), PLAYER_NAMES[playerIndex++ % PLAYER_NAMES.length], null, null, false);
    }

    // --- Constructor ---

    @Test
    void constructor_nullId_throws() {
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var players = new ArrayList<LobbyPlayer>();
        assertThatNullPointerException()
                .isThrownBy(() -> new Lobby(
                        null, gameId, hostPlayerId, "ABC123", players, MIN_PLAYERS, MAX_PLAYERS, FIXED_CLOCK));
    }

    @Test
    void constructor_nullGameId_throws() {
        var id = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var players = new ArrayList<LobbyPlayer>();
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Lobby(id, null, hostPlayerId, "ABC123", players, MIN_PLAYERS, MAX_PLAYERS, FIXED_CLOCK));
    }

    @Test
    void constructor_nullHostPlayerId_throws() {
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var players = new ArrayList<LobbyPlayer>();
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new Lobby(id, gameId, null, "ABC123", players, MIN_PLAYERS, MAX_PLAYERS, FIXED_CLOCK));
    }

    @Test
    void constructor_nullJoinCode_throws() {
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var players = new ArrayList<LobbyPlayer>();
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Lobby(id, gameId, hostPlayerId, null, players, MIN_PLAYERS, MAX_PLAYERS, FIXED_CLOCK));
    }

    @Test
    void constructor_nullPlayers_throws() {
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Lobby(id, gameId, hostPlayerId, "ABC123", null, MIN_PLAYERS, MAX_PLAYERS, FIXED_CLOCK));
    }

    @Test
    void constructor_initialStatusIsWaiting() {
        assertThat(emptyLobby().status()).isEqualTo(LobbyStatus.WAITING);
    }

    @Test
    @DisplayName("constructor registers LobbyCreated with correct lobbyId and hostPlayerId")
    void constructor_registersLobbyCreatedEvent() {
        // given
        var id = UUID.randomUUID();
        var hostId = UUID.randomUUID();

        // when
        var lobby = new Lobby(
                id, UUID.randomUUID(), hostId, "ABC123", new ArrayList<>(), MIN_PLAYERS, MAX_PLAYERS, FIXED_CLOCK);

        // then
        var events = lobby.pullEvents();
        assertThat(events).singleElement().isInstanceOf(LobbyCreated.class);
        var event = (LobbyCreated) events.getFirst();
        assertThat(event.lobbyId()).isEqualTo(id);
        assertThat(event.hostPlayerId()).isEqualTo(hostId);
    }

    @Test
    @DisplayName("reconstitute does not register any events")
    void reconstitute_doesNotRegisterEvents() {
        assertThat(lobbyWith(player()).pullEvents()).isEmpty();
    }

    // --- join() ---

    @Test
    void join_addsPlayer() {
        var lobby = emptyLobby();
        lobby.join(player());
        assertThat(lobby.currentPlayers()).hasSize(1);
    }

    @Test
    void join_stampsJoinedAtFromClock() {
        var lobby = emptyLobby();
        lobby.join(player());
        assertThat(lobby.currentPlayers().getFirst().joinedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void join_lobbyAlreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start();
        var extra = player();
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> lobby.join(extra));
    }

    @Test
    void join_lobbyFull_throws() {
        var lobby = emptyLobby();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            lobby.join(player());
        }
        var extra = player();
        assertThatExceptionOfType(LobbyFullException.class).isThrownBy(() -> lobby.join(extra));
    }

    @Test
    @DisplayName("join registers PlayerJoinedLobby with correct playerId and playerName")
    void join_registersPlayerJoinedLobbyEvent() {
        // given
        var lobby = emptyLobby();
        lobby.pullEvents(); // discard LobbyCreated
        var p = player();

        // when
        lobby.join(p);

        // then
        var events = lobby.pullEvents();
        assertThat(events).singleElement().isInstanceOf(PlayerJoinedLobby.class);
        var event = (PlayerJoinedLobby) events.getFirst();
        assertThat(event.playerId()).isEqualTo(p.playerId());
        assertThat(event.playerName()).isEqualTo(p.playerName());
    }

    // --- leave() ---

    @Test
    @DisplayName("non-host player leaves — removed from list and PlayerLeftLobby registered")
    void leave_nonHost_removesPlayerAndReturnsNonHostLeft() {
        // given
        var host = player();
        var other = player();
        var lobby = lobbyWith(host, other);

        // when
        lobby.leave(other.playerId());

        // then
        assertThat(lobby.currentPlayers()).doesNotContain(other);
        assertThat(lobby.pullEvents()).singleElement().isInstanceOf(PlayerLeftLobby.class);
    }

    @Test
    @DisplayName("host leaves with others present — transfers to earliest-joined; two events registered")
    void leave_hostLeavesWithOthersPresent_transfersHostAndReturnsHostTransferred() {
        // given
        var host = playerWithJoinedAt(Instant.parse("2025-01-01T10:00:00Z"));
        var earliest = playerWithJoinedAt(Instant.parse("2025-01-01T10:01:00Z"));
        var later = playerWithJoinedAt(Instant.parse("2025-01-01T10:02:00Z"));
        var lobby = lobbyWithHost(host, List.of(host, earliest, later));

        // when
        lobby.leave(host.playerId());

        // then
        assertThat(lobby.currentPlayers()).doesNotContain(host);
        assertThat(lobby.hostPlayerId()).isEqualTo(earliest.playerId());
        var events = lobby.pullEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(HostTransferred.class);
        assertThat(((HostTransferred) events.get(0)).newHostId()).isEqualTo(earliest.playerId());
        assertThat(events.get(1)).isInstanceOf(PlayerLeftLobby.class);
    }

    @Test
    @DisplayName("host leaves as sole player — lobby closed and LobbyClosed registered")
    void leave_hostLeavesSolePlayer_closesLobbyAndReturnsLobbyClosed() {
        // given
        var host = player();
        var lobby = lobbyWith(host);

        // when
        lobby.leave(host.playerId());

        // then
        assertThat(lobby.currentPlayers()).isEmpty();
        assertThat(lobby.status()).isEqualTo(LobbyStatus.CLOSED);
        assertThat(lobby.pullEvents()).singleElement().isInstanceOf(LobbyClosed.class);
    }

    @Test
    @DisplayName("player not in lobby — throws PlayerNotInLobbyException")
    void leave_playerNotInLobby_throws() {
        var lobby = emptyLobby();
        assertThatExceptionOfType(PlayerNotInLobbyException.class).isThrownBy(() -> lobby.leave(UUID.randomUUID()));
    }

    @Test
    @DisplayName("leave after lobby started — throws LobbyAlreadyStartedException")
    void leave_lobbyAlreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start();
        var nonHost = lobby.currentPlayers().get(1).playerId();
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> lobby.leave(nonHost));
    }

    // --- start() ---

    @Test
    void start_happyPath_statusBecomesStarted() {
        var lobby = lobbyReadyToStart();
        lobby.start();
        assertThat(lobby.status()).isEqualTo(LobbyStatus.STARTED);
    }

    @Test
    void start_tooFewPlayers_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.PROPHETS));
        assertThatExceptionOfType(NotEnoughPlayersException.class).isThrownBy(() -> lobby.start());
    }

    @Test
    void start_factionNotAssigned_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.PROPHETS));
        lobby.join(player(null));
        assertThatExceptionOfType(FactionNotAssignedException.class).isThrownBy(() -> lobby.start());
    }

    @Test
    void start_duplicateFaction_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.PROPHETS));
        assertThatExceptionOfType(DuplicateFactionException.class).isThrownBy(() -> lobby.start());
    }

    @Test
    void start_alreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start();
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> lobby.start());
    }

    Lobby lobbyReadyToStart() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.PROPHETS));
        lobby.join(player(Faction.REVISIONISTS));
        return lobby;
    }

    /** Lobby whose first player is the host. */
    Lobby lobbyWith(LobbyPlayer... players) {
        var hostId = players[0].playerId();
        return lobbyWithHost(players[0], List.of(players));
    }

    /** Lobby with an explicit host and a pre-populated player list (no join() stamps applied). */
    Lobby lobbyWithHost(LobbyPlayer host, List<LobbyPlayer> players) {
        return Lobby.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                host.playerId(),
                JOIN_CODE,
                new ArrayList<>(players),
                LobbyStatus.WAITING,
                MIN_PLAYERS,
                MAX_PLAYERS,
                FIXED_CLOCK);
    }

    LobbyPlayer playerWithJoinedAt(Instant joinedAt) {
        return new LobbyPlayer(
                UUID.randomUUID(), PLAYER_NAMES[playerIndex++ % PLAYER_NAMES.length], null, joinedAt, true);
    }

    // --- requestStart() ---

    @Test
    @DisplayName("host requests start with 3+ connected players — returns GameStarted")
    void requestStart_hostWithEnoughConnectedPlayers_returnsGameStarted() {
        // given
        var lobby = lobbyReadyToStart();
        var hostId = lobby.hostPlayerId();

        // when
        var outcome = lobby.requestStart(hostId);

        // then
        assertThat(outcome).isInstanceOf(StartOutcome.GameStarted.class);
    }

    @Test
    @DisplayName("non-host requests start — returns NotHost")
    void requestStart_nonHost_returnsNotHost() {
        // given
        var lobby = lobbyReadyToStart();
        var nonHostId = lobby.currentPlayers().stream()
                .filter(p -> !p.playerId().equals(lobby.hostPlayerId()))
                .findFirst()
                .orElseThrow()
                .playerId();

        // when
        var outcome = lobby.requestStart(nonHostId);

        // then
        assertThat(outcome).isInstanceOf(StartOutcome.NotHost.class);
    }

    @Test
    @DisplayName("host requests start with fewer than min players — returns NotEnoughPlayers")
    void requestStart_tooFewPlayers_returnsNotEnoughPlayers() {
        // given
        var host = player();
        var lobby = lobbyWith(host, player());

        // when
        var outcome = lobby.requestStart(host.playerId());

        // then
        assertThat(outcome).isInstanceOf(StartOutcome.NotEnoughPlayers.class);
    }

    @Test
    @DisplayName("host requests start but a player is disconnected — returns HasDisconnectedPlayers")
    void requestStart_disconnectedPlayer_returnsHasDisconnectedPlayers() {
        // given
        var host = player();
        var disconnected = disconnectedPlayer();
        var lobby = lobbyWith(host, player(), disconnected);

        // when
        var outcome = lobby.requestStart(host.playerId());

        // then
        assertThat(outcome).isInstanceOf(StartOutcome.HasDisconnectedPlayers.class);
        assertThat(((StartOutcome.HasDisconnectedPlayers) outcome).disconnectedPlayerIds())
                .containsExactly(disconnected.playerId());
    }

    // --- assignFaction() ---

    @Test
    @DisplayName("assign faction to player — faction stored on the correct player")
    void assignFaction_validPlayer_factionAssigned() {
        // given
        var lobby = emptyLobby();
        var p = player();
        lobby.join(p);
        var playerId = lobby.currentPlayers().getFirst().playerId();

        // when
        lobby.assignFaction(playerId, Faction.ERASERS);

        // then
        assertThat(lobby.currentPlayers().getFirst().faction()).isEqualTo(Faction.ERASERS);
    }

    @Test
    @DisplayName("assign faction to unknown player — throws PlayerNotInLobbyException")
    void assignFaction_unknownPlayer_throws() {
        // given
        var lobby = emptyLobby();

        // when / then
        assertThatExceptionOfType(PlayerNotInLobbyException.class)
                .isThrownBy(() -> lobby.assignFaction(UUID.randomUUID(), Faction.ERASERS));
    }

    // --- markPlayerDisconnected() / markPlayerReconnected() ---

    @Test
    @DisplayName("mark connected player as disconnected — connected becomes false")
    void markPlayerDisconnected_connectedPlayer_connectedFalse() {
        // given
        var lobby = emptyLobby();
        lobby.join(player());
        var playerId = lobby.currentPlayers().getFirst().playerId();

        // when
        lobby.markPlayerDisconnected(playerId);

        // then
        assertThat(lobby.currentPlayers().getFirst().connected()).isFalse();
    }

    @Test
    @DisplayName("mark disconnected player as reconnected — connected becomes true")
    void markPlayerReconnected_disconnectedPlayer_connectedTrue() {
        // given
        var lobby = emptyLobby();
        lobby.join(player());
        var playerId = lobby.currentPlayers().getFirst().playerId();
        lobby.markPlayerDisconnected(playerId);

        // when
        lobby.markPlayerReconnected(playerId);

        // then
        assertThat(lobby.currentPlayers().getFirst().connected()).isTrue();
    }
}
