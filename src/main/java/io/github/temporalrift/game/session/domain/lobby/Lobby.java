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

import io.github.temporalrift.events.session.HostTransferred;
import io.github.temporalrift.events.session.LobbyClosed;
import io.github.temporalrift.events.session.LobbyCreated;
import io.github.temporalrift.events.session.PlayerJoinedLobby;
import io.github.temporalrift.events.session.PlayerLeftLobby;
import io.github.temporalrift.game.shared.AggregateRoot;

public class Lobby extends AggregateRoot {

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
        registerEvent(new LobbyCreated(id, hostPlayerId, clock.instant()));
    }

    private Lobby(
            UUID id,
            UUID gameId,
            UUID hostPlayerId,
            String joinCode,
            List<LobbyPlayer> currentPlayers,
            LobbyStatus status,
            int minPlayers,
            int maxPlayers,
            Clock clock) {
        this.id = id;
        this.gameId = gameId;
        this.hostPlayerId = hostPlayerId;
        this.joinCode = joinCode;
        this.currentPlayers = new ArrayList<>(currentPlayers);
        this.status = status;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.clock = clock;
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
        return new Lobby(id, gameId, hostPlayerId, joinCode, currentPlayers, status, minPlayers, maxPlayers, clock);
    }

    public void join(LobbyPlayer player) {
        Objects.requireNonNull(player, "Player must not be null");
        requireWaiting();
        if (currentPlayers.size() >= maxPlayers) {
            throw new LobbyFullException();
        }
        currentPlayers.add(
                new LobbyPlayer(player.playerId(), player.playerName(), player.faction(), clock.instant(), true));
        registerEvent(new PlayerJoinedLobby(id, player.playerId(), player.playerName()));
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

    public void leave(UUID leavingPlayerId) {
        Objects.requireNonNull(leavingPlayerId, "Player id must not be null");
        requireWaiting();
        var removed = currentPlayers.removeIf(player -> player.playerId().equals(leavingPlayerId));
        if (!removed) {
            throw new PlayerNotInLobbyException(leavingPlayerId);
        }
        if (!leavingPlayerId.equals(hostPlayerId)) {
            registerEvent(new PlayerLeftLobby(id, leavingPlayerId));
            return;
        }
        if (currentPlayers.isEmpty()) {
            status = LobbyStatus.CLOSED;
            registerEvent(new LobbyClosed(id, gameId));
            return;
        }
        var newHost = currentPlayers.stream()
                .min(Comparator.comparing(LobbyPlayer::joinedAt))
                .orElseThrow();
        hostPlayerId = newHost.playerId();
        registerEvent(new HostTransferred(id, leavingPlayerId, newHost.playerId()));
        registerEvent(new PlayerLeftLobby(id, leavingPlayerId));
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
