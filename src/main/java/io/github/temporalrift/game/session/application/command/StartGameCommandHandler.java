package io.github.temporalrift.game.session.application.command;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.session.application.saga.GameStartSaga;
import io.github.temporalrift.game.session.domain.lobby.DisconnectedPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotHostException;
import io.github.temporalrift.game.session.domain.lobby.StartOutcome;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Service
class StartGameCommandHandler implements StartGameUseCase {

    private final LobbyRepository lobbyRepository;
    private final GameStartSaga gameStartSaga;

    StartGameCommandHandler(LobbyRepository lobbyRepository, GameStartSaga gameStartSaga) {
        this.lobbyRepository = lobbyRepository;
        this.gameStartSaga = gameStartSaga;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var lobby = lobbyRepository
                .findById(command.lobbyId())
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + command.lobbyId()));

        return switch (lobby.requestStart(command.requestingPlayerId())) {
            case StartOutcome.GameStarted ignored -> {
                gameStartSaga.start(lobby.gameId(), lobby);
                yield new Result(lobby.gameId());
            }
            case StartOutcome.NotHost ignored -> throw new NotHostException();
            case StartOutcome.NotEnoughPlayers(var count, var min) -> throw new NotEnoughPlayersException(count, min);
            case StartOutcome.HasDisconnectedPlayers(var ids) -> throw new DisconnectedPlayersException(ids);
        };
    }
}
