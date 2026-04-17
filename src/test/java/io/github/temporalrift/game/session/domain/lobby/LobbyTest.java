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
        return new LobbyPlayer(UUID.randomUUID(), PLAYER_NAMES[playerIndex++ % PLAYER_NAMES.length], faction, null);
    }

    LobbyPlayer player() {
        return player(null);
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

    // --- leave() ---

    @Test
    @DisplayName("non-host player leaves — removed from list and NonHostLeft returned")
    void leave_nonHost_removesPlayerAndReturnsNonHostLeft() {
        // given
        var host = player();
        var other = player();
        var lobby = lobbyWith(host, other);

        // when
        var outcome = lobby.leave(other.playerId());

        // then
        assertThat(lobby.currentPlayers()).doesNotContain(other);
        assertThat(outcome).isInstanceOf(LeaveOutcome.NonHostLeft.class);
    }

    @Test
    @DisplayName("host leaves with others present — transfers to earliest-joined and returns HostTransferred")
    void leave_hostLeavesWithOthersPresent_transfersHostAndReturnsHostTransferred() {
        // given
        var host = playerWithJoinedAt(Instant.parse("2025-01-01T10:00:00Z"));
        var earliest = playerWithJoinedAt(Instant.parse("2025-01-01T10:01:00Z"));
        var later = playerWithJoinedAt(Instant.parse("2025-01-01T10:02:00Z"));
        var lobby = lobbyWithHost(host, List.of(host, earliest, later));

        // when
        var outcome = lobby.leave(host.playerId());

        // then
        assertThat(lobby.currentPlayers()).doesNotContain(host);
        assertThat(lobby.hostPlayerId()).isEqualTo(earliest.playerId());
        assertThat(outcome).isEqualTo(new LeaveOutcome.HostTransferred(earliest.playerId()));
    }

    @Test
    @DisplayName("host leaves as sole player — lobby closed and LobbyClosed returned")
    void leave_hostLeavesSolePlayer_closesLobbyAndReturnsLobbyClosed() {
        // given
        var host = player();
        var lobby = lobbyWith(host);

        // when
        var outcome = lobby.leave(host.playerId());

        // then
        assertThat(lobby.currentPlayers()).isEmpty();
        assertThat(lobby.status()).isEqualTo(LobbyStatus.CLOSED);
        assertThat(outcome).isInstanceOf(LeaveOutcome.LobbyClosed.class);
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
        return new LobbyPlayer(UUID.randomUUID(), PLAYER_NAMES[playerIndex++ % PLAYER_NAMES.length], null, joinedAt);
    }
}
