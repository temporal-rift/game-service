package com.temporalrift.game.session.domain.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class Lobby {

    private final UUID id;

    private final UUID hostPlayerId;

    private final String joinCode;

    private final List<LobbyPlayer> currentPlayers;

    private final int minPlayers;

    private final int maxPlayers;

    private LobbyStatus status;

    public Lobby(
            UUID id,
            UUID hostPlayerId,
            String joinCode,
            List<LobbyPlayer> currentPlayers,
            int minPlayers,
            int maxPlayers) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.hostPlayerId = Objects.requireNonNull(hostPlayerId, "hostPlayerId must not be null");
        this.joinCode = Objects.requireNonNull(joinCode, "joinCode must not be null");
        this.currentPlayers =
                new ArrayList<>(Objects.requireNonNull(currentPlayers, "currentPlayers must not be null"));
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.status = LobbyStatus.WAITING;
    }

    public void join(LobbyPlayer player) {
        Objects.requireNonNull(player, "Player must not be null");
        requireWaiting();
        requireMaxPlayers();
        currentPlayers.add(player);
    }

    public void leave(UUID leavingPlayerId) {
        Objects.requireNonNull(leavingPlayerId, "Player id must not be null");
        requireWaiting();
        if (leavingPlayerId.equals(hostPlayerId)) {
            throw new HostCannotLeaveException();
        }
        currentPlayers.removeIf(player -> player.playerId().equals(leavingPlayerId));
    }

    public void start() {
        requireWaiting();
        requireMinPlayers();
        requireMaxPlayers();
        requireFactionAssignments();
        status = LobbyStatus.STARTED;
    }

    private void requireWaiting() {
        if (status != LobbyStatus.WAITING) {
            throw new LobbyAlreadyStartedException();
        }
    }

    private void requireMinPlayers() {
        if (currentPlayers.size() < minPlayers) {
            throw new NotEnoughPlayersException();
        }
    }

    private void requireMaxPlayers() {
        if (currentPlayers.size() >= maxPlayers) {
            throw new LobbyFullException();
        }
    }

    private void requireFactionAssignments() {
        if (currentPlayers.stream().map(LobbyPlayer::faction).anyMatch(Objects::isNull)) {
            throw new FactionNotAssignedException();
        }
        var factions = currentPlayers.stream().map(LobbyPlayer::faction).collect(Collectors.toSet());
        if (factions.size() != currentPlayers.size()) {
            throw new DuplicateFactionException();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID hostPlayerId() {
        return hostPlayerId;
    }

    public String joinCode() {
        return joinCode;
    }

    public LobbyStatus status() {
        return status;
    }

    public List<LobbyPlayer> currentPlayers() {
        return Collections.unmodifiableList(currentPlayers);
    }
}
