package com.temporalrift.game.session.domain.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.temporalrift.events.shared.Faction;

class LobbyTest {

    static final String JOIN_CODE = "X7K2P9";
    static final int MIN_PLAYERS = 3;
    static final int MAX_PLAYERS = 5;
    static final String[] PLAYER_NAMES = {"Alice", "Bob", "Charlie", "Diana", "Eve"};

    int playerIndex = 0;

    Lobby emptyLobby() {
        return new Lobby(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), JOIN_CODE, new ArrayList<>());
    }

    LobbyPlayer player(Faction faction) {
        return new LobbyPlayer(UUID.randomUUID(), PLAYER_NAMES[playerIndex++ % PLAYER_NAMES.length], false, faction);
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
        assertThatNullPointerException().isThrownBy(() -> new Lobby(null, gameId, hostPlayerId, "ABC123", players));
    }

    @Test
    void constructor_nullGameId_throws() {
        var id = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var players = new ArrayList<LobbyPlayer>();
        assertThatNullPointerException().isThrownBy(() -> new Lobby(id, null, hostPlayerId, "ABC123", players));
    }

    @Test
    void constructor_nullHostPlayerId_throws() {
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var players = new ArrayList<LobbyPlayer>();
        assertThatNullPointerException().isThrownBy(() -> new Lobby(id, gameId, null, "ABC123", players));
    }

    @Test
    void constructor_nullJoinCode_throws() {
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        var players = new ArrayList<LobbyPlayer>();
        assertThatNullPointerException().isThrownBy(() -> new Lobby(id, gameId, hostPlayerId, null, players));
    }

    @Test
    void constructor_nullPlayers_throws() {
        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostPlayerId = UUID.randomUUID();
        assertThatNullPointerException().isThrownBy(() -> new Lobby(id, gameId, hostPlayerId, "ABC123", null));
    }

    @Test
    void constructor_initialStatusIsWaiting() {
        assertThat(emptyLobby().status()).isEqualTo(LobbyStatus.WAITING);
    }

    // --- join() ---

    @Test
    void join_addsPlayer() {
        var lobby = emptyLobby();
        lobby.join(player(), MAX_PLAYERS);
        assertThat(lobby.currentPlayers()).hasSize(1);
    }

    @Test
    void join_lobbyAlreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start(MIN_PLAYERS);
        var extra = player();
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> lobby.join(extra, MAX_PLAYERS));
    }

    @Test
    void join_lobbyFull_throws() {
        var lobby = emptyLobby();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            lobby.join(player(), MAX_PLAYERS);
        }
        var extra = player();
        assertThatExceptionOfType(LobbyFullException.class).isThrownBy(() -> lobby.join(extra, MAX_PLAYERS));
    }

    // --- leave() ---

    @Test
    void leave_removesPlayer() {
        var lobby = emptyLobby();
        var p = player();
        lobby.join(p, MAX_PLAYERS);
        lobby.leave(p.playerId());
        assertThat(lobby.currentPlayers()).isEmpty();
    }

    @Test
    void leave_hostLeaves_throws() {
        var lobby = emptyLobby();
        assertThatExceptionOfType(HostCannotLeaveException.class).isThrownBy(() -> lobby.leave(lobby.hostPlayerId()));
    }

    @Test
    void leave_lobbyAlreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start(MIN_PLAYERS);
        var nonHost = lobby.currentPlayers().get(1).playerId();
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> lobby.leave(nonHost));
    }

    // --- start() ---

    @Test
    void start_happyPath_statusBecomesStarted() {
        var lobby = lobbyReadyToStart();
        lobby.start(MIN_PLAYERS);
        assertThat(lobby.status()).isEqualTo(LobbyStatus.STARTED);
    }

    @Test
    void start_tooFewPlayers_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS), MAX_PLAYERS);
        lobby.join(player(Faction.PROPHETS), MAX_PLAYERS);
        assertThatExceptionOfType(NotEnoughPlayersException.class).isThrownBy(() -> lobby.start(MIN_PLAYERS));
    }

    @Test
    void start_factionNotAssigned_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS), MAX_PLAYERS);
        lobby.join(player(Faction.PROPHETS), MAX_PLAYERS);
        lobby.join(player(null), MAX_PLAYERS);
        assertThatExceptionOfType(FactionNotAssignedException.class).isThrownBy(() -> lobby.start(MIN_PLAYERS));
    }

    @Test
    void start_duplicateFaction_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS), MAX_PLAYERS);
        lobby.join(player(Faction.ERASERS), MAX_PLAYERS);
        lobby.join(player(Faction.PROPHETS), MAX_PLAYERS);
        assertThatExceptionOfType(DuplicateFactionException.class).isThrownBy(() -> lobby.start(MIN_PLAYERS));
    }

    @Test
    void start_alreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start(MIN_PLAYERS);
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> lobby.start(MIN_PLAYERS));
    }

    Lobby lobbyReadyToStart() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS), MAX_PLAYERS);
        lobby.join(player(Faction.PROPHETS), MAX_PLAYERS);
        lobby.join(player(Faction.REVISIONISTS), MAX_PLAYERS);
        return lobby;
    }
}
