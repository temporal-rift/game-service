package io.github.temporalrift.game.session.application.saga;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.event.PlayerAbandoned;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

@Component
public class PlayerAbandonmentProcessor {

    private final PlayerReconnectSagaStateManager stateManager;
    private final LobbyRepository lobbyRepository;
    private final PlayerReconnectTimerRegistry timerRegistry;
    private final SessionEventPublisher eventPublisher;
    private final Clock clock;

    PlayerAbandonmentProcessor(
            PlayerReconnectSagaStateManager stateManager,
            LobbyRepository lobbyRepository,
            PlayerReconnectTimerRegistry timerRegistry,
            SessionEventPublisher eventPublisher,
            Clock clock) {
        this.stateManager = stateManager;
        this.lobbyRepository = lobbyRepository;
        this.timerRegistry = timerRegistry;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Caller holds era and active game locks; returns the locked roster after reconciling its deadlines. */
    public Lobby abandonDuePlayers(Game game) {
        var lobby = lobbyRepository
                .findByIdWithLock(game.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));
        abandonDuePlayers(game.id(), lobby, clock.instant());
        return lobby;
    }

    /** Caller holds era, game, and lobby locks; every due forfeit precedes the ending decision. */
    boolean abandonDuePlayers(UUID gameId, Lobby lobby, Instant decisionAt) {
        boolean changed = false;
        for (var saga : stateManager.findGracePeriodsDueBy(gameId, decisionAt)) {
            if (!stateManager.tryAbandon(saga.sagaId())) {
                continue;
            }
            timerRegistry.remove(saga.sagaId());
            lobby.markPlayerAbandoned(saga.playerId());
            eventPublisher.publish(DomainEventEnvelope.create(
                    gameId,
                    Game.AGGREGATE_TYPE,
                    gameId,
                    DomainEventEnvelope.SCHEMA_VERSION_V1,
                    new PlayerAbandoned(gameId, saga.playerId()),
                    clock));
            changed = true;
        }
        if (changed) {
            lobbyRepository.save(lobby);
        }
        return changed;
    }
}
