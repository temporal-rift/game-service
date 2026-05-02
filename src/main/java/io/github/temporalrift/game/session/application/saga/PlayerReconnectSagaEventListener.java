package io.github.temporalrift.game.session.application.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Component
class PlayerReconnectSagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectSagaEventListener.class);

    private final PlayerReconnectSaga saga;
    private final PlayerReconnectSagaStateManager stateManager;
    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;

    PlayerReconnectSagaEventListener(
            PlayerReconnectSaga saga,
            PlayerReconnectSagaStateManager stateManager,
            GameRepository gameRepository,
            LobbyRepository lobbyRepository) {
        this.saga = saga;
        this.stateManager = stateManager;
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
    }

    @ApplicationModuleListener
    void onPlayerDisconnected(PlayerDisconnectedApplicationEvent event) {
        var gameOpt = gameRepository.findById(event.gameId());
        if (gameOpt.isEmpty()) {
            return;
        }
        var game = gameOpt.get();
        var lobbyOpt = lobbyRepository.findById(game.lobbyId());
        if (lobbyOpt.isEmpty()) {
            return;
        }
        if (lobbyOpt.get().status() != LobbyStatus.STARTED) {
            return;
        }
        if (stateManager.hasActiveGracePeriod(event.gameId(), event.playerId())) {
            log.debug(
                    "Disconnect ignored for player {} in game {} — grace period already active",
                    event.playerId(),
                    event.gameId());
            return;
        }
        saga.start(event.gameId(), event.playerId());
    }

    @ApplicationModuleListener
    void onPlayerReconnected(PlayerReconnectedApplicationEvent event) {
        saga.handleReconnect(event.gameId(), event.playerId());
    }
}
