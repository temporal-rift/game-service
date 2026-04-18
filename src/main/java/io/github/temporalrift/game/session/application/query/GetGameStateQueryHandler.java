package io.github.temporalrift.game.session.application.query;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.GetGameStateUseCase;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Service
class GetGameStateQueryHandler implements GetGameStateUseCase {

    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;

    GetGameStateQueryHandler(GameRepository gameRepository, LobbyRepository lobbyRepository) {
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        var game = gameRepository
                .findById(query.gameId())
                .orElseThrow(() -> new NoSuchElementException("Game not found: " + query.gameId()));
        var lobby = lobbyRepository
                .findById(game.lobbyId())
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + game.lobbyId()));
        return new Result(
                game.id(),
                game.status(),
                game.eraCounter(),
                lobby.currentPlayers().size(),
                game.cascadedParadoxCounter());
    }
}
