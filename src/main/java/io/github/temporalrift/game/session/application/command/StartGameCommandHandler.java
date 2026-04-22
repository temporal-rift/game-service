package io.github.temporalrift.game.session.application.command;

import org.springframework.stereotype.Service;

import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.session.application.saga.StartGameSaga;
import io.github.temporalrift.game.session.domain.lobby.DisconnectedPlayersException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotLobbyHostException;
import io.github.temporalrift.game.session.domain.lobby.StartOutcome;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Service
class StartGameCommandHandler implements StartGameUseCase {

    private final LobbyRepository lobbyRepository;
    private final StartGameSaga startGameSaga;

    StartGameCommandHandler(LobbyRepository lobbyRepository, StartGameSaga startGameSaga) {
        this.lobbyRepository = lobbyRepository;
        this.startGameSaga = startGameSaga;
    }

    @Override
    public Result handle(Command command) {
        var lobby = lobbyRepository
                .findById(command.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(command.lobbyId()));

        return switch (lobby.requestStart(command.requestingPlayerId())) {
            case StartOutcome.GameStarted() -> {
                startGameSaga.start(lobby.gameId(), lobby);
                yield new Result(lobby.gameId());
            }
            case StartOutcome.NotHost() -> throw new NotLobbyHostException();
            case StartOutcome.NotEnoughPlayers(var count, var min) -> throw new NotEnoughPlayersException(count, min);
            case StartOutcome.HasDisconnectedPlayers(var ids) -> throw new DisconnectedPlayersException(ids);
        };
    }
}
