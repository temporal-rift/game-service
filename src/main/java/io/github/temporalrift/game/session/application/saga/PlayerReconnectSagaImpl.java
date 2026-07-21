package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.event.PlayerAbandoned;
import io.github.temporalrift.game.session.domain.event.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

@Service
class PlayerReconnectSagaImpl implements PlayerReconnectSaga {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectSagaImpl.class);

    private final LobbyRepository lobbyRepository;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlayerReconnectSagaStateManager stateManager;
    private final SessionGameRulesPort gameRules;
    private final PlayerReconnectTimerRegistry timerRegistry;
    private final Clock clock;

    PlayerReconnectSagaImpl(
            LobbyRepository lobbyRepository,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            PlayerReconnectSagaStateManager stateManager,
            SessionGameRulesPort gameRules,
            PlayerReconnectTimerRegistry timerRegistry,
            Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stateManager = stateManager;
        this.gameRules = gameRules;
        this.timerRegistry = timerRegistry;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public StartResult start(UUID gameId, UUID playerId) {
        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));

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

        return new StartResult(sagaId, graceExpiresAt);
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void handleReconnect(UUID gameId, UUID playerId) {
        var sagaOpt = stateManager.findByGameIdAndPlayerId(gameId, playerId);
        if (sagaOpt.isEmpty()) {
            log.info("Reconnect rejected for player {} in game {} — no reconnect saga", playerId, gameId);
            return;
        }

        var saga = sagaOpt.get();
        // The atomic claim, not the read above, decides who acts: reconnect races timer expiry
        // (in-memory timer, sweep, other instances) and only one transition may win.
        if (!stateManager.tryReconnect(saga.sagaId())) {
            log.info("Reconnect rejected for player {} in game {} — saga not in GRACE_PERIOD", playerId, gameId);
            return;
        }
        timerRegistry.cancel(saga.sagaId());

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));
        lobby.markPlayerReconnected(playerId);
        lobbyRepository.save(lobby);
    }

    void handleTimerExpiry(UUID sagaId) {
        var sagaOpt = stateManager.findBySagaId(sagaId);
        if (sagaOpt.isEmpty()) {
            log.debug("Timer expiry ignored for saga {} — unknown saga", sagaId);
            return;
        }

        var saga = sagaOpt.get();
        // The atomic claim, not the read above, decides who acts: the in-memory timer, the sweep on
        // this and every other instance, and a concurrent reconnect all race for this transition,
        // and PlayerAbandoned must be published exactly once.
        if (!stateManager.tryAbandon(sagaId)) {
            log.debug("Timer expiry ignored for saga {} — not in GRACE_PERIOD (idempotent)", sagaId);
            return;
        }
        timerRegistry.remove(sagaId);

        eventPublisher.publish(DomainEventEnvelope.create(
                saga.gameId(),
                Game.AGGREGATE_TYPE,
                saga.gameId(),
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new PlayerAbandoned(saga.gameId(), saga.playerId()),
                clock));

        // Locked game read: the last-player check below is a cross-saga decision. Without
        // serialization, two final grace periods expiring concurrently each see the other's
        // uncommitted saga as still GRACE_PERIOD and both skip the game-over publication.
        // Whoever locks second re-counts after the first commit and publishes exactly once.
        var game = gameRepository
                .findByIdWithLock(saga.gameId())
                .orElseThrow(() -> new GameNotFoundException(saga.gameId()));
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));

        var connectedCount =
                lobby.currentPlayers().stream().filter(LobbyPlayer::connected).count();
        var gracePeriodCount = stateManager.countActiveGracePeriodForGame(saga.gameId());

        if (connectedCount == 0 && gracePeriodCount == 0) {
            var payload = new GameEndedAbnormally(saga.gameId(), "all-players-abandoned");
            eventPublisher.publish(DomainEventEnvelope.create(
                    saga.gameId(),
                    Game.AGGREGATE_TYPE,
                    saga.gameId(),
                    DomainEventEnvelope.SCHEMA_VERSION_V1,
                    payload,
                    clock));
            applicationEventPublisher.publishEvent(payload);
        }
    }
}
