package io.github.temporalrift.game.session.domain.lobby;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class Lobby {

    private final UUID id;

    private final UUID gameId;

    private final UUID hostPlayerId;

    private final String joinCode;

    private final List<LobbyPlayer> currentPlayers;

    private final int minPlayers;

    private final int maxPlayers;

    private final Clock clock;

    private LobbyStatus status;

    public Lobby(
            UUID id,
            UUID gameId,
            UUID hostPlayerId,
            String joinCode,
            List<LobbyPlayer> currentPlayers,
            int minPlayers,
            int maxPlayers,
            Clock clock) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.hostPlayerId = Objects.requireNonNull(hostPlayerId, "hostPlayerId must not be null");
        this.joinCode = Objects.requireNonNull(joinCode, "joinCode must not be null");
        this.currentPlayers =
                new ArrayList<>(Objects.requireNonNull(currentPlayers, "currentPlayers must not be null"));
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.status = LobbyStatus.WAITING;
    }

    public static Lobby reconstitute(
            UUID id,
            UUID gameId,
            UUID hostPlayerId,
            String joinCode,
            List<LobbyPlayer> currentPlayers,
            LobbyStatus status,
            int minPlayers,
            int maxPlayers,
            Clock clock) {
        var lobby = new Lobby(id, gameId, hostPlayerId, joinCode, currentPlayers, minPlayers, maxPlayers, clock);
        lobby.status = status;
        return lobby;
    }

    public void join(LobbyPlayer player) {
        Objects.requireNonNull(player, "Player must not be null");
        requireWaiting();
        if (currentPlayers.size() >= maxPlayers) {
            throw new LobbyFullException();
        }
        currentPlayers.add(new LobbyPlayer(player.playerId(), player.playerName(), player.faction(), clock.instant()));
    }

    public void leave(UUID leavingPlayerId) {
        Objects.requireNonNull(leavingPlayerId, "Player id must not be null");
        requireWaiting();
        if (leavingPlayerId.equals(hostPlayerId)) {
            throw new HostCannotLeaveException();
        }
        var removed = currentPlayers.removeIf(player -> player.playerId().equals(leavingPlayerId));
        if (!removed) {
            throw new PlayerNotInLobbyException(leavingPlayerId);
        }
    }

    public void start() {
        requireWaiting();
        if (currentPlayers.size() < minPlayers) {
            throw new NotEnoughPlayersException();
        }
        requireFactionAssignments();
        status = LobbyStatus.STARTED;
    }

    private void requireWaiting() {
        if (status != LobbyStatus.WAITING) {
            throw new LobbyAlreadyStartedException();
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

    public UUID gameId() {
        return gameId;
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

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public List<LobbyPlayer> currentPlayers() {
        return Collections.unmodifiableList(currentPlayers);
    }
}
