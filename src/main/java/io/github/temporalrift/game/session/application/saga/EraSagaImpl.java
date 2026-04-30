package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.action.EventsDrawn;
import io.github.temporalrift.events.action.HandDealt;
import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.GameEndedAbnormally;
import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.InsufficientDeckException;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@Service
class EraSagaImpl implements EraSaga {

    private static final CardType[] CARD_POOL = CardType.values();

    private final GameRepository gameRepository;
    private final FutureEventCatalogPort futureEventCatalog;
    private final SessionEventPublisher eventPublisher;
    private final EraSagaStateManager stateManager;
    private final GameRulesPort gameRules;
    private final SecureRandom random;

    EraSagaImpl(
            GameRepository gameRepository,
            FutureEventCatalogPort futureEventCatalog,
            SessionEventPublisher eventPublisher,
            EraSagaStateManager stateManager,
            GameRulesPort gameRules) {
        this.gameRepository = gameRepository;
        this.futureEventCatalog = futureEventCatalog;
        this.eventPublisher = eventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.random = new SecureRandom();
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void start(UUID gameId, int eraNumber, List<UUID> playerIds, List<UUID> cascadedEventIds) {
        stateManager.initRunning(gameId, eraNumber, playerIds);

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));

        try {
            var drawnIds = game.startEra(cascadedEventIds.size(), gameRules.eventsPerEra());
            gameRepository.save(game);

            publishEventsDrawn(game, gameId, eraNumber, drawnIds, cascadedEventIds);
            playerIds.forEach(playerId -> publishHandDealt(game, gameId, eraNumber, playerId));

            stateManager.advanceTo(gameId, EraSagaStatus.WAITING_ROUND_1);
        } catch (InsufficientDeckException _) {
            stateManager.fail(gameId);
            eventPublisher.publish(EventEnvelope.create(
                    game.id(), Game.AGGREGATE_TYPE, gameId, 1, new GameEndedAbnormally(gameId, "deck-exhausted")));
        }
    }

    private void publishEventsDrawn(
            Game game, UUID gameId, int eraNumber, List<UUID> drawnIds, List<UUID> cascadedIds) {
        var allIds = new ArrayList<>(drawnIds);
        allIds.addAll(cascadedIds);
        var definitions = futureEventCatalog.findByEventIds(allIds);

        var events = buildFutureEventList(definitions, drawnIds.size());
        eventPublisher.publish(EventEnvelope.create(
                game.id(), Game.AGGREGATE_TYPE, gameId, 1, new EventsDrawn(gameId, eraNumber, events)));
    }

    private List<EventsDrawn.FutureEvent> buildFutureEventList(
            List<FutureEventDefinition> definitions, int drawnCount) {
        var events = new ArrayList<EventsDrawn.FutureEvent>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            var def = definitions.get(i);
            var isCascaded = i >= drawnCount;
            var outcomes = def.outcomes().stream()
                    .map(o -> new EventsDrawn.Outcome(o.outcomeId(), o.description(), o.probability()))
                    .toList();
            events.add(new EventsDrawn.FutureEvent(def.eventId(), def.title(), outcomes, isCascaded));
        }
        return events;
    }

    private void publishHandDealt(Game game, UUID gameId, int eraNumber, UUID playerId) {
        var cards = IntStream.range(0, gameRules.cardsPerHand())
                .mapToObj(
                        i -> new HandDealt.CardInstance(UUID.randomUUID(), CARD_POOL[random.nextInt(CARD_POOL.length)]))
                .toList();
        eventPublisher.publish(EventEnvelope.create(
                game.id(), Game.AGGREGATE_TYPE, gameId, 1, new HandDealt(gameId, eraNumber, playerId, cards)));
    }
}
