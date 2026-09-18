package io.github.temporalrift.game.session.application.listener;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.ForesightRevealRepository;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.event.ForesightDeclared;
import io.github.temporalrift.game.shared.domain.event.ForesightRevealed;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

@Component
class ForesightRevealListener {

    private static final Logger log = LoggerFactory.getLogger(ForesightRevealListener.class);

    private final GameRepository gameRepository;
    private final FutureEventCatalogPort catalog;
    private final ForesightRevealRepository reveals;
    private final SessionEventPublisher eventPublisher;
    private final SessionGameRulesPort gameRules;
    private final Clock clock;

    ForesightRevealListener(
            GameRepository gameRepository,
            FutureEventCatalogPort catalog,
            ForesightRevealRepository reveals,
            SessionEventPublisher eventPublisher,
            SessionGameRulesPort gameRules,
            Clock clock) {
        this.gameRepository = gameRepository;
        this.catalog = catalog;
        this.reveals = reveals;
        this.eventPublisher = eventPublisher;
        this.gameRules = gameRules;
        this.clock = clock;
    }

    @ApplicationModuleListener
    @Transactional
    void onForesightDeclared(ForesightDeclared declared) {
        var gameId = declared.gameId();
        var eraNumber = declared.eraNumber();
        var viewer = declared.playerId();
        if (reveals.findByGameIdAndEraNumberAndPlayerId(gameId, eraNumber, viewer)
                .isPresent()) {
            return;
        }
        var game = gameRepository.findById(gameId).orElse(null);
        if (game == null) {
            log.info("Foresight reveal skipped for game {} — game not found", gameId);
            return;
        }
        // Read-only peek at the deck head: the deck is never consumed, reordered, or saved here.
        var previewIds = previewIds(game, eraNumber);
        var emptyReason = previewIds.isEmpty() ? emptyReason(game, eraNumber) : null;
        var stored = new ForesightReveal(gameId, eraNumber, viewer, eraNumber + 1, previewIds, emptyReason);
        if (!reveals.saveIfAbsent(stored)) {
            return;
        }
        eventPublisher.publish(DomainEventEnvelope.create(
                game.id(),
                Game.AGGREGATE_TYPE,
                gameId,
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new ForesightRevealed(
                        gameId, eraNumber, viewer, eraNumber + 1, revealedEvents(previewIds), emptyReason),
                clock));
    }

    private List<UUID> previewIds(Game game, int eraNumber) {
        if (eraNumber >= gameRules.maxEras()) {
            return List.of();
        }
        var deck = game.eventDeck();
        return List.copyOf(deck.subList(0, Math.min(gameRules.eventsPerEra(), deck.size())));
    }

    private String emptyReason(Game game, int eraNumber) {
        if (eraNumber >= gameRules.maxEras()) {
            return "final-era";
        }
        return "deck-exhausted";
    }

    private List<ForesightRevealed.RevealedEvent> revealedEvents(List<UUID> previewIds) {
        if (previewIds.isEmpty()) {
            return List.of();
        }
        return catalog.findByEventIds(previewIds).stream()
                .map(definition -> new ForesightRevealed.RevealedEvent(
                        definition.eventId(),
                        definition.title(),
                        definition.outcomes().stream()
                                .map(outcome -> new ForesightRevealed.RevealedOutcome(
                                        outcome.outcomeId(), outcome.description()))
                                .toList()))
                .toList();
    }
}
