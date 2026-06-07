package io.github.temporalrift.game.session.application.command;

import org.springframework.stereotype.Service;

import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.session.application.saga.StartGameSaga;

@Service
class StartGameCommandHandler implements StartGameUseCase {

    private final StartGameSaga startGameSaga;

    StartGameCommandHandler(StartGameSaga startGameSaga) {
        this.startGameSaga = startGameSaga;
    }

    @Override
    public Result handle(Command command) {
        return new Result(startGameSaga.start(command.lobbyId(), command.requestingPlayerId()));
    }
}
