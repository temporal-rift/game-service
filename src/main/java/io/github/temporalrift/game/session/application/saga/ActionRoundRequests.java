package io.github.temporalrift.game.session.application.saga;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.shared.domain.event.StartActionRoundRequested;

/**
 * Builds the request that opens an action round. Abandonment is session state, so this is where the
 * participants who have not abandoned are resolved for the action module. It only reads: callers already hold
 * the era lock and the lobby is not locked here, which keeps the era, game, lobby lock order intact.
 */
@Component
class ActionRoundRequests {

    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;

    ActionRoundRequests(GameRepository gameRepository, LobbyRepository lobbyRepository) {
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
    }

    StartActionRoundRequested open(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {
        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        var lobby =
                lobbyRepository.findById(game.lobbyId()).orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()));
        var activePlayerIds =
                lobby.contenders().stream().map(LobbyPlayer::playerId).toList();
        return new StartActionRoundRequested(
                gameId,
                eraNumber,
                roundNumber,
                playerIds,
                playerIds.stream().filter(activePlayerIds::contains).toList());
    }
}
