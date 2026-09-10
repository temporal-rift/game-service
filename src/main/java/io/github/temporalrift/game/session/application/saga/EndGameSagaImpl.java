package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
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

import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.FactionRevealPort;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.FactionRevealed;
import io.github.temporalrift.game.shared.GameEnded;

@Service
class EndGameSagaImpl implements EndGameSaga {

    private static final Logger log = LoggerFactory.getLogger(EndGameSagaImpl.class);

    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;
    private final SessionEventPublisher eventPublisher;
    private final FactionRevealPort factionRevealPort;
    private final EndGameSagaStateManager stateManager;
    private final FinalScoreQueryPort finalScoreQueryPort;
    private final Clock clock;

    EndGameSagaImpl(
            GameRepository gameRepository,
            LobbyRepository lobbyRepository,
            SessionEventPublisher eventPublisher,
            FactionRevealPort factionRevealPort,
            EndGameSagaStateManager stateManager,
            FinalScoreQueryPort finalScoreQueryPort,
            Clock clock) {
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
        this.eventPublisher = eventPublisher;
        this.factionRevealPort = factionRevealPort;
        this.stateManager = stateManager;
        this.finalScoreQueryPort = finalScoreQueryPort;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    @Retryable(retryFor = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public void start(UUID gameId, EndGameTrigger triggerType, UUID... playerIds) {
        // Every trigger's detection point (EraSagaAdvancer's win/collapse/stabilization branches)
        // transitions the Game aggregate itself before publishing the event that reaches this saga, so
        // this saga never mutates Game -- it only finalizes (scores, GameEnded, FactionRevealed). The
        // atomic claim below is the single idempotency guard for a redelivered event, regardless of
        // trigger type.
        if (!stateManager.claimIfAbsent(gameId, triggerType, List.of(playerIds))) {
            log.info("EndGameSaga.start ignored for game {} — already handled", gameId);
            return;
        }

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));

        // The lobby roster is the system of record for assigned factions; start-game saga state is
        // workflow bookkeeping and never carries the assignments.
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));

        var finalScores = finalScoreQueryPort.getScores(gameId);
        publishEvent(gameId, new GameEnded(gameId, triggerType.name(), finalScores));

        var factionRevealed = new FactionRevealed(
                gameId,
                lobby.currentPlayers().stream()
                        .map(player -> new FactionRevealed.PlayerFactionResult(
                                player.playerId(), player.faction().name()))
                        .toList());
        // Kafka path for external services (timeline-service, read-service), plus a synchronous
        // in-transaction call into scoring's own bonus-award/visibility-flip logic -- both are
        // computable immediately from data already on hand, so there is no reason to route them
        // through Modulith's eventually-consistent async event dispatch.
        publishEvent(gameId, factionRevealed);
        factionRevealPort.reveal(factionRevealed);

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
