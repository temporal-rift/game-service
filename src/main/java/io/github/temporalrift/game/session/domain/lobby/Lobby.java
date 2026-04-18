package io.github.temporalrift.game.session.domain.lobby;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Lobby {

    public static final String AGGREGATE_TYPE = "Lobby";

    private final UUID id;

    private final UUID gameId;
    private final String joinCode;
    private final List<LobbyPlayer> currentPlayers;
    private final int minPlayers;
    private final int maxPlayers;
    private final Clock clock;
    private UUID hostPlayerId;
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
        currentPlayers.add(
                new LobbyPlayer(player.playerId(), player.playerName(), player.faction(), clock.instant(), true));
    }

    public void assignFaction(UUID playerId, io.github.temporalrift.events.shared.Faction faction) {
        requireWaiting();
        var index = playerIndex(playerId);
        currentPlayers.set(index, currentPlayers.get(index).withFaction(faction));
    }

    public void markPlayerDisconnected(UUID playerId) {
        var index = playerIndex(playerId);
        currentPlayers.set(index, currentPlayers.get(index).withConnected(false));
    }

    public void markPlayerReconnected(UUID playerId) {
        var index = playerIndex(playerId);
        currentPlayers.set(index, currentPlayers.get(index).withConnected(true));
    }

    private int playerIndex(UUID playerId) {
        return IntStream.range(0, currentPlayers.size())
                .filter(i -> currentPlayers.get(i).playerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new PlayerNotInLobbyException(playerId));
    }

    public LeaveOutcome leave(UUID leavingPlayerId) {
        Objects.requireNonNull(leavingPlayerId, "Player id must not be null");
        requireWaiting();
        var removed = currentPlayers.removeIf(player -> player.playerId().equals(leavingPlayerId));
        if (!removed) {
            throw new PlayerNotInLobbyException(leavingPlayerId);
        }
        if (!leavingPlayerId.equals(hostPlayerId)) {
            return new LeaveOutcome.NonHostLeft();
        }
        if (currentPlayers.isEmpty()) {
            status = LobbyStatus.CLOSED;
            return new LeaveOutcome.LobbyClosed();
        }
        var newHost = currentPlayers.stream()
                .min(Comparator.comparing(LobbyPlayer::joinedAt))
                .orElseThrow();
        hostPlayerId = newHost.playerId();
        return new LeaveOutcome.HostTransferred(newHost.playerId());
    }

    public StartOutcome requestStart(UUID requestingPlayerId) {
        Objects.requireNonNull(requestingPlayerId, "Requesting player id must not be null");
        requireWaiting();
        if (!requestingPlayerId.equals(hostPlayerId)) {
            return new StartOutcome.NotHost();
        }
        if (currentPlayers.size() < minPlayers) {
            return new StartOutcome.NotEnoughPlayers(currentPlayers.size(), minPlayers);
        }
        var disconnectedIds = currentPlayers.stream()
                .filter(p -> !p.connected())
                .map(LobbyPlayer::playerId)
                .toList();
        if (!disconnectedIds.isEmpty()) {
            return new StartOutcome.HasDisconnectedPlayers(disconnectedIds);
        }
        return new StartOutcome.GameStarted();
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
