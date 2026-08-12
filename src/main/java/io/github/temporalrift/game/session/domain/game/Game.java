package io.github.temporalrift.game.session.domain.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.shared.AggregateRoot;

public class Game extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "Game";

    private final UUID id;
    private final UUID lobbyId;
    private final List<UUID> eventDeck;
    private final List<PendingCarryOverEvent> pendingCarryOverEvents;
    /** Every drawn event's per-game eventId, mapped to the catalog card and outcome IDs it was drawn with. */
    private final Map<UUID, DrawnFutureEvent> drawnEvents;

    private int eraCounter;
    private int cascadedParadoxCounter;
    private GameStatus status;

    public Game(UUID id, UUID lobbyId, List<UUID> eventDeck) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.lobbyId = Objects.requireNonNull(lobbyId, "lobbyId must not be null");
        this.eventDeck = new ArrayList<>(Objects.requireNonNull(eventDeck, "eventDeck must not be null"));
        this.pendingCarryOverEvents = new ArrayList<>();
        this.drawnEvents = new HashMap<>();
        this.eraCounter = 0;
        this.cascadedParadoxCounter = 0;
        this.status = GameStatus.IN_PROGRESS;
    }

    private Game(UUID id, UUID lobbyId, List<UUID> eventDeck, GameProgress progress) {
        this.id = id;
        this.lobbyId = lobbyId;
        this.eventDeck = new ArrayList<>(eventDeck);
        this.pendingCarryOverEvents = new ArrayList<>(progress.pendingCarryOverEvents());
        this.drawnEvents = new HashMap<>(progress.drawnEvents());
        this.eraCounter = progress.eraCounter();
        this.cascadedParadoxCounter = progress.cascadedParadoxCounter();
        this.status = progress.status();
    }

    public static Game reconstitute(
            UUID id,
            UUID lobbyId,
            List<UUID> eventDeck,
            int eraCounter,
            int cascadedParadoxCounter,
            GameStatus status) {
        return reconstitute(
                id,
                lobbyId,
                eventDeck,
                new GameProgress(eraCounter, cascadedParadoxCounter, List.of(), Map.of(), status));
    }

    public static Game reconstitute(UUID id, UUID lobbyId, List<UUID> eventDeck, GameProgress progress) {
        return new Game(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(lobbyId, "lobbyId must not be null"),
                Objects.requireNonNull(eventDeck, "eventDeck must not be null"),
                Objects.requireNonNull(progress, "progress must not be null"));
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
        // >= so a threshold lowered mid-game (or a counter past the limit) still ends the game.
        if (cascadedParadoxCounter >= maxCascadedParadoxes) {
            status = GameStatus.ENDED_BY_COLLAPSE;
        }
    }

    /**
     * Applies a complete era's cascades in the rules-defined reveal order.
     *
     * @return the event that crossed the global cascade threshold, or {@code null} when the threshold was not reached
     */
    public UUID recordCascadedParadoxesInRevealOrder(List<UUID> cascadedEventIds, int maxCascadedParadoxes) {
        requireInProgress();
        UUID collapsingEventId = null;
        for (var eventId : cascadedEventIds) {
            cascadedParadoxCounter++;
            if (collapsingEventId == null && cascadedParadoxCounter >= maxCascadedParadoxes) {
                status = GameStatus.ENDED_BY_COLLAPSE;
                collapsingEventId = eventId;
            }
        }
        return collapsingEventId;
    }

    public void recordPendingCarryOverEvents(List<PendingCarryOverEvent> carryOverEvents) {
        requireInProgress();
        pendingCarryOverEvents.clear();
        pendingCarryOverEvents.addAll(Objects.requireNonNull(carryOverEvents, "carryOverEvents must not be null"));
    }

    /**
     * Records this era's freshly-drawn events, merged into every event drawn so far this game — a game only ever
     * draws a bounded, small number of events, so entries for events that have already resolved are left in place
     * rather than pruned.
     */
    public void recordDrawnEvents(Map<UUID, DrawnFutureEvent> eventIdToDrawnEvent) {
        requireInProgress();
        drawnEvents.putAll(Objects.requireNonNull(eventIdToDrawnEvent, "eventIdToDrawnEvent must not be null"));
    }

    /** The catalog card and original per-game outcome IDs a drawn event's {@code eventId} was drawn with. */
    public DrawnFutureEvent drawnEvent(UUID eventId) {
        var drawnEvent = drawnEvents.get(eventId);
        if (drawnEvent == null) {
            throw new IllegalStateException("No drawn-event record for event " + eventId);
        }
        return drawnEvent;
    }

    public List<PendingCarryOverEvent> drainPendingCarryOverEvents() {
        requireInProgress();
        var carryOverEvents = List.copyOf(pendingCarryOverEvents);
        pendingCarryOverEvents.clear();
        return carryOverEvents;
    }

    public void endEra(int maxEras) {
        requireInProgress();
        if (eraCounter >= maxEras) {
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

    public List<PendingCarryOverEvent> pendingCarryOverEvents() {
        return Collections.unmodifiableList(pendingCarryOverEvents);
    }

    public Map<UUID, DrawnFutureEvent> drawnEvents() {
        return Collections.unmodifiableMap(drawnEvents);
    }
}
