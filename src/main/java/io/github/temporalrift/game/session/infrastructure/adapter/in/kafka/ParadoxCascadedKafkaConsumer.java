package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.TimelineCollapsed;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.timeline.ParadoxCascaded;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameAlreadyOverException;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;

@Component
class ParadoxCascadedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParadoxCascadedKafkaConsumer.class);
    private static final String EVENT_TYPE = "timeline.ParadoxCascaded";
    private static final Set<Faction> COLLAPSE_WINNERS = Set.of(Faction.ERASERS, Faction.REVISIONISTS);

    private final GameRepository gameRepository;
    private final StartGameSagaRepository startGameSagaRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final GameRulesPort gameRules;
    private final ObjectMapper objectMapper;

    ParadoxCascadedKafkaConsumer(
            GameRepository gameRepository,
            StartGameSagaRepository startGameSagaRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            GameRulesPort gameRules,
            ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.startGameSagaRepository = startGameSagaRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.gameRules = gameRules;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
        var paradox = objectMapper.convertValue(envelope.payload(), ParadoxCascaded.class);
        var gameId = paradox.gameId();

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        try {
            game.recordCascadedParadox(gameRules.maxCascadedParadoxes());
        } catch (GameAlreadyOverException e) {
            log.info("ParadoxCascaded ignored for game {} — already over", gameId);
            return;
        }
        gameRepository.save(game);

        if (game.status() == GameStatus.ENDED_BY_COLLAPSE) {
            var assignments = startGameSagaRepository
                    .findByGameId(gameId)
                    .orElseThrow(() -> new GameNotFoundException(gameId))
                    .factionAssignments();

            var collapsed = buildTimelineCollapsed(gameId, paradox.eraNumber(), assignments);
            eventPublisher.publish(EventEnvelope.create(gameId, Game.AGGREGATE_TYPE, gameId, 1, collapsed));
            applicationEventPublisher.publishEvent(collapsed);
        }
    }

    private TimelineCollapsed buildTimelineCollapsed(
            java.util.UUID gameId, int eraNumber, List<FactionAssignment> assignments) {
        var winners = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        var losers = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        for (var assignment : assignments) {
            var result = new TimelineCollapsed.PlayerFactionResult(
                    assignment.playerId(), assignment.faction().name());
            if (COLLAPSE_WINNERS.contains(assignment.faction())) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        return new TimelineCollapsed(gameId, eraNumber, winners, losers);
    }
}
