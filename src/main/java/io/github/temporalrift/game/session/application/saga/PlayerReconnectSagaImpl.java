package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.GameEndedAbnormally;
import io.github.temporalrift.events.session.PlayerAbandoned;
import io.github.temporalrift.events.session.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@Service
class PlayerReconnectSagaImpl implements PlayerReconnectSaga {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectSagaImpl.class);

    private final LobbyRepository lobbyRepository;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlayerReconnectSagaStateManager stateManager;
    private final GameRulesPort gameRules;
    private final PlayerReconnectTimerRegistry timerRegistry;

    PlayerReconnectSagaImpl(
            LobbyRepository lobbyRepository,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            PlayerReconnectSagaStateManager stateManager,
            GameRulesPort gameRules,
            PlayerReconnectTimerRegistry timerRegistry) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.timerRegistry = timerRegistry;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public StartResult start(UUID gameId, UUID playerId) {
        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));

        var sagaId = UUID.randomUUID();
        var graceExpiresAt = Instant.now().plusSeconds(gameRules.reconnectGracePeriodSeconds());
        stateManager.initGracePeriod(sagaId, gameId, playerId, graceExpiresAt);

        lobby.markPlayerDisconnected(playerId);
        lobbyRepository.save(lobby);

        eventPublisher.publish(EventEnvelope.create(
                lobby.id(), Lobby.AGGREGATE_TYPE, gameId, 1, new PlayerDisconnected(gameId, playerId)));

        return new StartResult(sagaId, graceExpiresAt);
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void handleReconnect(UUID gameId, UUID playerId) {
        var sagaOpt = stateManager
                .findByGameIdAndPlayerId(gameId, playerId)
                .filter(s -> s.status() == PlayerReconnectSagaStatus.GRACE_PERIOD);

        if (sagaOpt.isEmpty()) {
            log.info("Reconnect rejected for player {} in game {} — saga not in GRACE_PERIOD", playerId, gameId);
            return;
        }

        var saga = sagaOpt.get();
        stateManager.reconnect(saga.sagaId());
        timerRegistry.cancel(saga.sagaId());

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));
        lobby.markPlayerReconnected(playerId);
        lobbyRepository.save(lobby);
    }

    void handleTimerExpiry(UUID sagaId) {
        var sagaOpt =
                stateManager.findBySagaId(sagaId).filter(s -> s.status() == PlayerReconnectSagaStatus.GRACE_PERIOD);

        if (sagaOpt.isEmpty()) {
            log.debug("Timer expiry ignored for saga {} — not in GRACE_PERIOD (idempotent)", sagaId);
            return;
        }

        var saga = sagaOpt.get();
        stateManager.abandon(sagaId);
        timerRegistry.remove(sagaId);

        eventPublisher.publish(EventEnvelope.create(
                saga.gameId(),
                Game.AGGREGATE_TYPE,
                saga.gameId(),
                1,
                new PlayerAbandoned(saga.gameId(), saga.playerId())));

        var game = gameRepository.findById(saga.gameId()).orElseThrow(() -> new GameNotFoundException(saga.gameId()));
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));

        var connectedCount = lobby.currentPlayers().stream()
                .filter(io.github.temporalrift.game.session.domain.lobby.LobbyPlayer::connected)
                .count();
        var gracePeriodCount = stateManager.countActiveGracePeriodForGame(saga.gameId());

        if (connectedCount == 0 && gracePeriodCount == 0) {
            var payload = new GameEndedAbnormally(saga.gameId(), "all-players-abandoned");
            eventPublisher.publish(EventEnvelope.create(saga.gameId(), Game.AGGREGATE_TYPE, saga.gameId(), 1, payload));
            applicationEventPublisher.publishEvent(payload);
        }
    }
}
