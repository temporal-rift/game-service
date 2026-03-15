package com.temporalrift.game.session.domain.lobby;

import com.temporalrift.events.shared.Faction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class LobbyTest {

    static final String JOIN_CODE = "X7K2P9";
    static final int MIN_PLAYERS = 3;
    static final int MAX_PLAYERS = 5;
    static final String[] PLAYER_NAMES = {"Alice", "Bob", "Charlie", "Diana", "Eve"};
    
    int playerIndex = 0;

    Lobby emptyLobby() {
        return new Lobby(UUID.randomUUID(), UUID.randomUUID(), JOIN_CODE, new ArrayList<>(), MIN_PLAYERS, MAX_PLAYERS);
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
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Lobby(null, UUID.randomUUID(), "ABC123", new ArrayList<>(), MIN_PLAYERS, MAX_PLAYERS));
    }

    @Test
    void constructor_nullHostPlayerId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Lobby(UUID.randomUUID(), null, "ABC123", new ArrayList<>(), MIN_PLAYERS, MAX_PLAYERS));
    }

    @Test
    void constructor_nullJoinCode_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Lobby(
                        UUID.randomUUID(), UUID.randomUUID(), null, new ArrayList<>(), MIN_PLAYERS, MAX_PLAYERS));
    }

    @Test
    void constructor_nullPlayers_throws() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Lobby(UUID.randomUUID(), UUID.randomUUID(), "ABC123", null, MIN_PLAYERS, MAX_PLAYERS));
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
    void join_lobbyAlreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start();
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(() -> lobby.join(player()));
    }

    @Test
    void join_lobbyFull_throws() {
        var lobby = emptyLobby();
        for (int i = 0; i < MAX_PLAYERS; i++) lobby.join(player());
        assertThatExceptionOfType(LobbyFullException.class).isThrownBy(() -> lobby.join(player()));
    }

    // --- leave() ---

    @Test
    void leave_removesPlayer() {
        var lobby = emptyLobby();
        var p = player();
        lobby.join(p);
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
        assertThatExceptionOfType(NotEnoughPlayersException.class).isThrownBy(lobby::start);
    }

    @Test
    void start_factionNotAssigned_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.PROPHETS));
        lobby.join(player(null));
        assertThatExceptionOfType(FactionNotAssignedException.class).isThrownBy(lobby::start);
    }

    @Test
    void start_duplicateFaction_throws() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.PROPHETS));
        assertThatExceptionOfType(DuplicateFactionException.class).isThrownBy(lobby::start);
    }

    @Test
    void start_alreadyStarted_throws() {
        var lobby = lobbyReadyToStart();
        lobby.start();
        assertThatExceptionOfType(LobbyAlreadyStartedException.class).isThrownBy(lobby::start);
    }

    Lobby lobbyReadyToStart() {
        var lobby = emptyLobby();
        lobby.join(player(Faction.ERASERS));
        lobby.join(player(Faction.PROPHETS));
        lobby.join(player(Faction.REVISIONISTS));
        return lobby;
    }
}
