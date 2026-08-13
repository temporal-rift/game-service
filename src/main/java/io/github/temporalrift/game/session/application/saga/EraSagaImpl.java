package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.dealing.WeightedCardDealer;
import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.game.DrawnFutureEvent;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.InsufficientDeckException;
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.CarryOverState;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.StartActionRoundRequested;

@Service
class EraSagaImpl implements EraSaga {

    private static final Logger log = LoggerFactory.getLogger(EraSagaImpl.class);

    private final GameRepository gameRepository;
    private final FutureEventCatalogPort futureEventCatalog;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EraSagaStateManager stateManager;
    private final SessionGameRulesPort gameRules;
    private final WeightedCardDealer cardDealer;
    private final Clock clock;

    EraSagaImpl(
            GameRepository gameRepository,
            FutureEventCatalogPort futureEventCatalog,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            EraSagaStateManager stateManager,
            SessionGameRulesPort gameRules,
            WeightedCardDealer cardDealer,
            Clock clock) {
        this.gameRepository = gameRepository;
        this.futureEventCatalog = futureEventCatalog;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.cardDealer = cardDealer;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void start(UUID gameId, int eraNumber, List<UUID> playerIds, List<PendingCarryOverEvent> carryOverEvents) {
        stateManager.initRunning(gameId, eraNumber, playerIds);

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));

        try {
            var drawnIds = game.startEra(carryOverEvents.size(), gameRules.eventsPerEra());
            var freshDraw = toFreshFutureEvents(drawnIds);
            game.recordDrawnEvents(freshDraw.eventIdToDrawnEvent());
            gameRepository.save(game);

            publishEventsDrawn(game, gameId, eraNumber, freshDraw.events(), carryOverEvents);
            playerIds.forEach(playerId -> publishHandDealt(game, gameId, eraNumber, playerId));

            stateManager.advanceTo(gameId, EraSagaStatus.WAITING_ROUND_1);
            applicationEventPublisher.publishEvent(new StartActionRoundRequested(gameId, eraNumber, 1, playerIds));
        } catch (InsufficientDeckException e) {
            log.warn("Deck exhausted for game {} era {} — ending game abnormally", gameId, eraNumber, e);
            stateManager.fail(gameId);
            eventPublisher.publish(DomainEventEnvelope.create(
                    game.id(),
                    Game.AGGREGATE_TYPE,
                    gameId,
                    DomainEventEnvelope.SCHEMA_VERSION_V1,
                    new GameEndedAbnormally(gameId, "deck-exhausted"),
                    clock));
        }
    }

    private void publishEventsDrawn(
            Game game,
            UUID gameId,
            int eraNumber,
            List<EventsDrawn.FutureEvent> freshEvents,
            List<PendingCarryOverEvent> carryOverEvents) {
        var events = Stream.concat(freshEvents.stream(), toCarryOverFutureEvents(game, carryOverEvents).stream())
                .toList();
        var eventsDrawn = new EventsDrawn(gameId, eraNumber, events);
        eventPublisher.publish(DomainEventEnvelope.create(
                game.id(), Game.AGGREGATE_TYPE, gameId, DomainEventEnvelope.SCHEMA_VERSION_V1, eventsDrawn, clock));
        applicationEventPublisher.publishEvent(eventsDrawn);
    }

    /**
     * Mints a fresh, per-game-unique {@code eventId}/{@code outcomeId} for each drawn catalog card instead of
     * reusing the catalog's own fixed IDs (shared across every game) — see the {@code future-event-draw-identity}
     * capability. Returns the resulting {@code eventId -> DrawnFutureEvent} mapping alongside the events so the
     * caller can record it on {@link Game} for later carry-over lookups.
     */
    private FreshDraw toFreshFutureEvents(List<UUID> ids) {
        var eventIdToDrawnEvent = new HashMap<UUID, DrawnFutureEvent>();
        var events = futureEventCatalog.findByEventIds(ids).stream()
                .map(def -> {
                    var eventId = UUID.randomUUID();
                    var outcomes = def.outcomes().stream()
                            .map(o -> new EventsDrawn.Outcome(UUID.randomUUID(), o.description(), o.probability()))
                            .toList();
                    eventIdToDrawnEvent.put(
                            eventId,
                            new DrawnFutureEvent(
                                    def.eventId(),
                                    outcomes.stream()
                                            .map(EventsDrawn.Outcome::outcomeId)
                                            .toList()));
                    return new EventsDrawn.FutureEvent(eventId, def.title(), outcomes, CarryOverState.FRESH);
                })
                .toList();
        return new FreshDraw(events, Map.copyOf(eventIdToDrawnEvent));
    }

    private record FreshDraw(List<EventsDrawn.FutureEvent> events, Map<UUID, DrawnFutureEvent> eventIdToDrawnEvent) {}

    /**
     * A carried-over event republishes under the same {@code eventId} and the same {@code outcomeId}s it was
     * originally drawn with — never freshly minted ones. Timeline-service never re-drafts a carried-over event's
     * aggregate (its era-index entry is pre-inserted for the next era by {@code ResolveEraCommandHandler}/
     * {@code ParadoxResolutionSagaImpl}), so its outcome IDs never change after the first draft; republishing with
     * different IDs would make a subsequent action's {@code targetOutcomeId} pass game-service's own validation
     * but fail against timeline-service's actual aggregate.
     */
    private List<EventsDrawn.FutureEvent> toCarryOverFutureEvents(
            Game game, List<PendingCarryOverEvent> carryOverEvents) {
        Map<UUID, io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition> definitionsByCardId =
                futureEventCatalog
                        .findByEventIds(carryOverEvents.stream()
                                .map(carryOverEvent -> game.drawnEvent(carryOverEvent.eventId())
                                        .cardId())
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(
                                io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition::eventId,
                                Function.identity()));
        return carryOverEvents.stream()
                .map(carryOverEvent -> {
                    var drawnEvent = game.drawnEvent(carryOverEvent.eventId());
                    return toFutureEvent(
                            carryOverEvent.eventId(),
                            drawnEvent.outcomeIds(),
                            definitionsByCardId.get(drawnEvent.cardId()),
                            carryOverEvent.carryOverState());
                })
                .toList();
    }

    private EventsDrawn.FutureEvent toFutureEvent(
            UUID eventId,
            List<UUID> outcomeIds,
            io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition definition,
            CarryOverState carryOverState) {
        if (definition == null) {
            throw new IllegalStateException("Missing future event definition for carry-over event " + eventId);
        }
        var catalogOutcomes = definition.outcomes();
        var outcomes = IntStream.range(0, catalogOutcomes.size())
                .mapToObj(i -> new EventsDrawn.Outcome(
                        outcomeIds.get(i),
                        catalogOutcomes.get(i).description(),
                        catalogOutcomes.get(i).probability()))
                .toList();
        return new EventsDrawn.FutureEvent(eventId, definition.title(), outcomes, carryOverState);
    }

    private void publishHandDealt(Game game, UUID gameId, int eraNumber, UUID playerId) {
        var cards = cardDealer.deal(gameRules.cardsPerHand());
        var handDealt = new HandDealt(gameId, eraNumber, playerId, cards);
        eventPublisher.publish(DomainEventEnvelope.create(
                game.id(), Game.AGGREGATE_TYPE, gameId, DomainEventEnvelope.SCHEMA_VERSION_V1, handDealt, clock));
        applicationEventPublisher.publishEvent(handDealt);
    }
}
