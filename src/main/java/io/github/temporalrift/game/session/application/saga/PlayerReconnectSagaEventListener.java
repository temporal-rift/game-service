package io.github.temporalrift.game.session.application.saga;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Component
class PlayerReconnectSagaEventListener {

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
        gameRepository
                .findById(event.gameId())
                .flatMap(game -> lobbyRepository.findById(game.lobbyId()))
                .filter(lobby -> lobby.status() == LobbyStatus.STARTED)
                .filter(_ -> !stateManager.hasActiveGracePeriod(event.gameId(), event.playerId()))
                .ifPresent(_ -> saga.start(event.gameId(), event.playerId()));
    }

    @ApplicationModuleListener
    void onPlayerReconnected(PlayerReconnectedApplicationEvent event) {
        saga.handleReconnect(event.gameId(), event.playerId());
    }
}
