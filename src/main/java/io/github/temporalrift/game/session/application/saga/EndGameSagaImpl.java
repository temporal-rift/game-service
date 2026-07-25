package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameAlreadyOverException;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.FactionRevealed;
import io.github.temporalrift.game.shared.GameEnded;

@Service
class EndGameSagaImpl implements EndGameSaga {

    private static final Logger log = LoggerFactory.getLogger(EndGameSagaImpl.class);

    private final GameRepository gameRepository;
    private final StartGameSagaRepository startGameSagaRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EndGameSagaStateManager stateManager;
    private final FinalScoreQueryPort finalScoreQueryPort;
    private final Clock clock;

    EndGameSagaImpl(
            GameRepository gameRepository,
            StartGameSagaRepository startGameSagaRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            EndGameSagaStateManager stateManager,
            FinalScoreQueryPort finalScoreQueryPort,
            Clock clock) {
        this.gameRepository = gameRepository;
        this.startGameSagaRepository = startGameSagaRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stateManager = stateManager;
        this.finalScoreQueryPort = finalScoreQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    @Retryable(retryFor = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public void start(UUID gameId, EndGameTrigger triggerType, UUID... playerIds) {
        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        try {
            game.end();
        } catch (GameAlreadyOverException _) {
            log.info("EndGameSaga.start ignored for game {} — already over", gameId);
            return;
        }

        stateManager.initRunning(gameId, triggerType, List.of(playerIds));
        gameRepository.save(game);

        var assignments = startGameSagaRepository
                .findByGameId(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId))
                .factionAssignments();

        var finalScores = finalScoreQueryPort.getScores(gameId);
        publishEvent(gameId, new GameEnded(gameId, triggerType.name(), finalScores));

        var factionRevealed = new FactionRevealed(
                gameId,
                assignments.stream()
                        .map(assignment -> new FactionRevealed.PlayerFactionResult(
                                assignment.playerId(), assignment.faction().name()))
                        .toList());
        // Kafka path for external services, plus the in-process path the scoring module's
        // faction-visibility projection listens to (dual-publish pattern, see developer-notes.md).
        publishEvent(gameId, factionRevealed);
        applicationEventPublisher.publishEvent(factionRevealed);

        stateManager.complete(gameId);
    }

    @Recover
    void recoverStart(DataAccessException e, UUID gameId, EndGameTrigger triggerType, UUID... playerIds) {
        log.error("EndGameSaga persistence retries exhausted for game {}", gameId, e);
        stateManager.compensate(gameId);
    }

    private void publishEvent(UUID gameId, Object payload) {
        eventPublisher.publish(DomainEventEnvelope.create(
                gameId, Game.AGGREGATE_TYPE, gameId, DomainEventEnvelope.SCHEMA_VERSION_V1, payload, clock));
    }
}
