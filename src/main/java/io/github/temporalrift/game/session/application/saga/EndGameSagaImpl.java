package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.FactionRevealed;
import io.github.temporalrift.events.session.GameEnded;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameAlreadyOverException;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;

@Service
class EndGameSagaImpl implements EndGameSaga {

    private static final Logger log = LoggerFactory.getLogger(EndGameSagaImpl.class);

    private final GameRepository gameRepository;
    private final StartGameSagaRepository startGameSagaRepository;
    private final SessionEventPublisher eventPublisher;
    private final EndGameSagaStateManager stateManager;
    private final FinalScoreQueryPort finalScoreQueryPort;

    EndGameSagaImpl(
            GameRepository gameRepository,
            StartGameSagaRepository startGameSagaRepository,
            SessionEventPublisher eventPublisher,
            EndGameSagaStateManager stateManager,
            FinalScoreQueryPort finalScoreQueryPort) {
        this.gameRepository = gameRepository;
        this.startGameSagaRepository = startGameSagaRepository;
        this.eventPublisher = eventPublisher;
        this.stateManager = stateManager;
        this.finalScoreQueryPort = finalScoreQueryPort;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    @Retryable(retryFor = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public void start(UUID gameId, EndGameTrigger triggerType, UUID... playerIds) {
        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        try {
            game.end();
        } catch (GameAlreadyOverException e) {
            log.info("EndGameSaga.start ignored for game {} — already over", gameId);
            return;
        }

        var playerIdList = Arrays.asList(playerIds);
        stateManager.initRunning(gameId, triggerType, playerIdList);
        gameRepository.save(game);

        var assignments = startGameSagaRepository
                .findByGameId(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId))
                .factionAssignments();

        var finalScores = finalScoreQueryPort.getScores(gameId);
        publishEvent(gameId, new GameEnded(gameId, toEndReason(triggerType), finalScores));
        publishEvent(gameId, new FactionRevealed(gameId, toReveals(assignments)));

        stateManager.complete(gameId);
    }

    @Recover
    void recoverStart(DataAccessException e, UUID gameId, EndGameTrigger triggerType, UUID... playerIds) {
        log.error("EndGameSaga persistence retries exhausted for game {}", gameId, e);
        stateManager.compensate(gameId);
    }

    private void publishEvent(UUID gameId, Object payload) {
        eventPublisher.publish(EventEnvelope.create(gameId, Game.AGGREGATE_TYPE, gameId, 1, payload));
    }

    private static String toEndReason(EndGameTrigger trigger) {
        return switch (trigger) {
            case WIN_CONDITION_MET -> "WIN_CONDITION_MET";
            case TIMELINE_COLLAPSED -> "TIMELINE_COLLAPSED";
            case TIMELINE_STABILIZED -> "TIMELINE_STABILIZED";
        };
    }

    private static List<FactionRevealed.PlayerFactionResult> toReveals(List<FactionAssignment> assignments) {
        return assignments.stream()
                .map(a -> new FactionRevealed.PlayerFactionResult(
                        a.playerId(), a.faction().name()))
                .toList();
    }
}
