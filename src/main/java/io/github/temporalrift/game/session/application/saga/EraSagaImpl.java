package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.security.SecureRandom;
import java.time.Clock;
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

import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.InsufficientDeckException;
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.CarryOverState;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.StartActionRoundRequested;

@Service
class EraSagaImpl implements EraSaga {

    private static final Logger log = LoggerFactory.getLogger(EraSagaImpl.class);
    private static final CardType[] CARD_POOL = CardType.values();

    private final GameRepository gameRepository;
    private final FutureEventCatalogPort futureEventCatalog;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EraSagaStateManager stateManager;
    private final SessionGameRulesPort gameRules;
    private final SecureRandom random;
    private final Clock clock;

    EraSagaImpl(
            GameRepository gameRepository,
            FutureEventCatalogPort futureEventCatalog,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            EraSagaStateManager stateManager,
            SessionGameRulesPort gameRules,
            Clock clock) {
        this.gameRepository = gameRepository;
        this.futureEventCatalog = futureEventCatalog;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.random = new SecureRandom();
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void start(UUID gameId, int eraNumber, List<UUID> playerIds, List<PendingCarryOverEvent> carryOverEvents) {
        stateManager.initRunning(gameId, eraNumber, playerIds);

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));

        try {
            var drawnIds = game.startEra(carryOverEvents.size(), gameRules.eventsPerEra());
            gameRepository.save(game);

            publishEventsDrawn(game, gameId, eraNumber, drawnIds, carryOverEvents);
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
            Game game, UUID gameId, int eraNumber, List<UUID> drawnIds, List<PendingCarryOverEvent> carryOverEvents) {
        var events = Stream.concat(
                        toFreshFutureEvents(drawnIds).stream(), toCarryOverFutureEvents(carryOverEvents).stream())
                .toList();
        var eventsDrawn = new EventsDrawn(gameId, eraNumber, events);
        eventPublisher.publish(DomainEventEnvelope.create(
                game.id(), Game.AGGREGATE_TYPE, gameId, DomainEventEnvelope.SCHEMA_VERSION_V1, eventsDrawn, clock));
        applicationEventPublisher.publishEvent(eventsDrawn);
    }

    private List<EventsDrawn.FutureEvent> toFreshFutureEvents(List<UUID> ids) {
        return futureEventCatalog.findByEventIds(ids).stream()
                .map(def -> new EventsDrawn.FutureEvent(
                        def.eventId(),
                        def.title(),
                        def.outcomes().stream()
                                .map(o -> new EventsDrawn.Outcome(o.outcomeId(), o.description(), o.probability()))
                                .toList(),
                        CarryOverState.FRESH))
                .toList();
    }

    private List<EventsDrawn.FutureEvent> toCarryOverFutureEvents(List<PendingCarryOverEvent> carryOverEvents) {
        Map<UUID, io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition> definitionsById =
                futureEventCatalog
                        .findByEventIds(carryOverEvents.stream()
                                .map(PendingCarryOverEvent::eventId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(
                                io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition::eventId,
                                Function.identity()));
        return carryOverEvents.stream()
                .map(carryOverEvent ->
                        toFutureEvent(definitionsById.get(carryOverEvent.eventId()), carryOverEvent.carryOverState()))
                .toList();
    }

    private EventsDrawn.FutureEvent toFutureEvent(
            io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition definition,
            CarryOverState carryOverState) {
        if (definition == null) {
            throw new IllegalStateException("Missing future event definition for carry-over event");
        }
        return new EventsDrawn.FutureEvent(
                definition.eventId(),
                definition.title(),
                definition.outcomes().stream()
                        .map(o -> new EventsDrawn.Outcome(o.outcomeId(), o.description(), o.probability()))
                        .toList(),
                carryOverState);
    }

    private void publishHandDealt(Game game, UUID gameId, int eraNumber, UUID playerId) {
        var cards = IntStream.range(0, gameRules.cardsPerHand())
                .mapToObj(
                        i -> new HandDealt.CardInstance(UUID.randomUUID(), CARD_POOL[random.nextInt(CARD_POOL.length)]))
                .toList();
        var handDealt = new HandDealt(gameId, eraNumber, playerId, cards);
        eventPublisher.publish(DomainEventEnvelope.create(
                game.id(), Game.AGGREGATE_TYPE, gameId, DomainEventEnvelope.SCHEMA_VERSION_V1, handDealt, clock));
        applicationEventPublisher.publishEvent(handDealt);
    }
}
