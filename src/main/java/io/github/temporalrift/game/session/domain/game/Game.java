package io.github.temporalrift.game.session.domain.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.shared.AggregateRoot;

public class Game extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "Game";

    private final UUID id;
    private final UUID lobbyId;
    private final List<UUID> eventDeck;
    private int eraCounter;
    private int cascadedParadoxCounter;
    private GameStatus status;

    public Game(UUID id, UUID lobbyId, List<UUID> eventDeck) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.lobbyId = Objects.requireNonNull(lobbyId, "lobbyId must not be null");
        this.eventDeck = new ArrayList<>(Objects.requireNonNull(eventDeck, "eventDeck must not be null"));
        this.eraCounter = 0;
        this.cascadedParadoxCounter = 0;
        this.status = GameStatus.IN_PROGRESS;
    }

    private Game(
            UUID id,
            UUID lobbyId,
            List<UUID> eventDeck,
            int eraCounter,
            int cascadedParadoxCounter,
            GameStatus status) {
        this.id = id;
        this.lobbyId = lobbyId;
        this.eventDeck = new ArrayList<>(eventDeck);
        this.eraCounter = eraCounter;
        this.cascadedParadoxCounter = cascadedParadoxCounter;
        this.status = status;
    }

    public static Game reconstitute(
            UUID id,
            UUID lobbyId,
            List<UUID> eventDeck,
            int eraCounter,
            int cascadedParadoxCounter,
            GameStatus status) {
        return new Game(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(lobbyId, "lobbyId must not be null"),
                Objects.requireNonNull(eventDeck, "eventDeck must not be null"),
                eraCounter,
                cascadedParadoxCounter,
                Objects.requireNonNull(status, "status must not be null"));
    }

    public List<UUID> startEra(int carryOverCount, int eventsPerEra) {
        requireInProgress();
        var eventsNeeded = eventsPerEra - carryOverCount;
        requireDeckCapacity(eventsNeeded);
        eraCounter++;
        var drawn = new ArrayList<>(eventDeck.subList(0, eventsNeeded));
        eventDeck.subList(0, eventsNeeded).clear();
        return Collections.unmodifiableList(drawn);
    }

    public void recordCascadedParadox(int maxCascadedParadoxes) {
        requireInProgress();
        cascadedParadoxCounter++;
        if (cascadedParadoxCounter == maxCascadedParadoxes) {
            status = GameStatus.ENDED_BY_COLLAPSE;
        }
    }

    public void endEra(int maxEras) {
        requireInProgress();
        if (eraCounter == maxEras) {
            status = GameStatus.ENDED_BY_STABILIZATION;
        }
    }

    public void end() {
        requireInProgress();
        status = GameStatus.ENDED_BY_WIN;
    }

    private void requireInProgress() {
        if (status != GameStatus.IN_PROGRESS) {
            throw new GameAlreadyOverException();
        }
    }

    private void requireDeckCapacity(int eventsNeeded) {
        if (eventDeck.size() < eventsNeeded) {
            throw new InsufficientDeckException();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID lobbyId() {
        return lobbyId;
    }

    public int eraCounter() {
        return eraCounter;
    }

    public int cascadedParadoxCounter() {
        return cascadedParadoxCounter;
    }

    public GameStatus status() {
        return status;
    }

    public List<UUID> eventDeck() {
        return Collections.unmodifiableList(eventDeck);
    }
}
