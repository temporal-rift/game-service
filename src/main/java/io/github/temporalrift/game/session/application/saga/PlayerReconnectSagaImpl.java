package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.event.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;
import io.github.temporalrift.game.shared.domain.event.GameEnded;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

@Service
class PlayerReconnectSagaImpl implements PlayerReconnectSaga {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectSagaImpl.class);

    private final LobbyRepository lobbyRepository;
    private final GameRepository gameRepository;
    private final EraSagaRepository eraSagaRepository;
    private final SessionEventPublisher eventPublisher;
    private final AbandonmentEndingPublisher endingPublisher;
    private final PlayerAbandonmentProcessor abandonmentProcessor;
    private final FinalScoreQueryPort finalScoreQueryPort;
    private final PlayerReconnectSagaStateManager stateManager;
    private final SessionGameRulesPort gameRules;
    private final PlayerReconnectTimerRegistry timerRegistry;
    private final Clock clock;

    PlayerReconnectSagaImpl(
            LobbyRepository lobbyRepository,
            GameRepository gameRepository,
            EraSagaRepository eraSagaRepository,
            SessionEventPublisher eventPublisher,
            AbandonmentEndingPublisher endingPublisher,
            PlayerAbandonmentProcessor abandonmentProcessor,
            FinalScoreQueryPort finalScoreQueryPort,
            PlayerReconnectSagaStateManager stateManager,
            SessionGameRulesPort gameRules,
            PlayerReconnectTimerRegistry timerRegistry,
            Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.eraSagaRepository = eraSagaRepository;
        this.eventPublisher = eventPublisher;
        this.endingPublisher = endingPublisher;
        this.abandonmentProcessor = abandonmentProcessor;
        this.finalScoreQueryPort = finalScoreQueryPort;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.timerRegistry = timerRegistry;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public Optional<StartResult> start(UUID gameId, UUID playerId) {
        eraSagaRepository.findByGameIdWithLock(gameId);
        var game = gameRepository.findByIdWithLock(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.status() != GameStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        // Locked: save() rewrites the whole player collection, so concurrent connected-flag writes
        // for different players must serialize or the last writer erases the other's flag.
        var lobby = lobbyRepository
                .findByIdWithLock(game.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));

        if (lobby.isAbandoned(playerId)) {
            return Optional.empty();
        }
        var active = stateManager.findActiveGracePeriod(gameId, playerId);
        if (active.isPresent()) {
            return Optional.of(
                    new StartResult(active.get().sagaId(), active.get().graceExpiresAt()));
        }
        var sagaId = UUID.randomUUID();
        var graceExpiresAt = clock.instant().plusSeconds(gameRules.reconnectGracePeriodSeconds());
        stateManager.initGracePeriod(sagaId, gameId, playerId, graceExpiresAt);

        lobby.markPlayerDisconnected(playerId);
        lobbyRepository.save(lobby);

        eventPublisher.publish(DomainEventEnvelope.create(
                lobby.id(),
                Lobby.AGGREGATE_TYPE,
                gameId,
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new PlayerDisconnected(gameId, playerId),
                clock));

        return Optional.of(new StartResult(sagaId, graceExpiresAt));
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void handleReconnect(UUID gameId, UUID playerId) {
        stateManager
                .findActiveGracePeriod(gameId, playerId)
                .ifPresentOrElse(
                        saga -> reconnect(gameId, playerId, saga),
                        () -> log.info(
                                "Reconnect rejected for player {} in game {} — no reconnect saga", playerId, gameId));
    }

    private void reconnect(UUID gameId, UUID playerId, PlayerReconnectSagaState saga) {
        if (saga.status() != PlayerReconnectSagaStatus.GRACE_PERIOD) {
            return;
        }
        // Acquire the same locks before claiming a saga row, so peer-expiry reconciliation cannot deadlock.
        eraSagaRepository.findByGameIdWithLock(gameId);
        var game = gameRepository.findByIdWithLock(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.status() != GameStatus.IN_PROGRESS) {
            return;
        }
        var lobby = lobbyRepository
                .findByIdWithLock(game.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));
        var decisionAt = clock.instant();
        var claimed = stateManager
                .findActiveGracePeriod(gameId, playerId)
                .filter(state -> state.status() == PlayerReconnectSagaStatus.GRACE_PERIOD
                        && state.graceExpiresAt().isAfter(decisionAt))
                .filter(state -> stateManager.tryReconnect(state.sagaId()));
        if (claimed.isEmpty()) {
            log.info("Reconnect rejected for player {} in game {} — grace period no longer active", playerId, gameId);
            return;
        }
        timerRegistry.cancel(claimed.get().sagaId());
        lobby.markPlayerReconnected(playerId);
        lobbyRepository.save(lobby);
    }

    void handleTimerExpiry(UUID sagaId) {
        stateManager
                .findBySagaId(sagaId)
                .ifPresentOrElse(
                        this::abandonExpiredSaga,
                        () -> log.debug("Timer expiry ignored for saga {} — unknown saga", sagaId));
    }

    private void abandonExpiredSaga(PlayerReconnectSagaState saga) {
        var sagaId = saga.sagaId();
        if (saga.status() != PlayerReconnectSagaStatus.GRACE_PERIOD
                || saga.graceExpiresAt().isAfter(clock.instant())) {
            return;
        }

        var gameId = saga.gameId();
        // Claim grace rows only after the era -> game -> lobby locks shared by every ending decision.
        var eraSaga = eraSagaRepository.findByGameIdWithLock(gameId);
        var game = gameRepository.findByIdWithLock(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        // A grace period can outlive the game (another ending landed first); forfeiting then would brand a
        // player who never left an in-progress game.
        if (game.status() != GameStatus.IN_PROGRESS) {
            if (stateManager.tryComplete(sagaId)) {
                timerRegistry.remove(sagaId);
            }
            log.debug("Grace expiry for saga {} ignored — game {} already over", sagaId, gameId);
            return;
        }
        var lobby = lobbyRepository
                .findByIdWithLock(game.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));
        if (!abandonmentProcessor.abandonDuePlayers(gameId, lobby, clock.instant())) {
            return;
        }

        var contenders = lobby.contenders();
        if (contenders.size() > 1) {
            return;
        }
        // Era scoring commits only while the era saga awaits it; ending now would publish totals that scoring is
        // about to change, so the era-end decision ends the game once those scores are committed.
        if (eraSaga.filter(state -> state.status() == EraSagaStatus.WAITING_SCORES)
                .isPresent()) {
            log.info("Abandonment ending in game {} — deferred to the era's scoring boundary", gameId);
            return;
        }
        endAfterAbandonment(game, eraSaga, lobby);
    }

    private void endAfterAbandonment(Game game, Optional<EraSagaState> eraSaga, Lobby lobby) {
        var contenders = lobby.contenders();
        if (contenders.isEmpty()) {
            game.endAbnormally();
        } else {
            game.end();
        }
        gameRepository.save(game);
        eraSaga.filter(state -> state.status() != EraSagaStatus.COMPLETED && state.status() != EraSagaStatus.FAILED)
                .ifPresent(state -> eraSagaRepository.save(state.withStatus(EraSagaStatus.COMPLETED)));
        if (contenders.isEmpty()) {
            endingPublisher.publishAllPlayersAbandoned(game);
            return;
        }
        var winner = contenders.getFirst();
        var score = finalScoreQueryPort.getScores(game.id()).stream()
                .filter(result -> result.playerId().equals(winner.playerId()))
                .mapToInt(GameEnded.PlayerScoreResult::score)
                .findFirst()
                .orElse(0);
        endingPublisher.publishLastPlayerStanding(game, winner, score);
    }
}
