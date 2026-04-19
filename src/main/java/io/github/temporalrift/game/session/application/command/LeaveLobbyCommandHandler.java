package io.github.temporalrift.game.session.application.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Service
class LeaveLobbyCommandHandler implements LeaveLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    LeaveLobbyCommandHandler(LobbyRepository lobbyRepository) {
        this.lobbyRepository = lobbyRepository;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var lobby = lobbyRepository
                .findById(command.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(command.lobbyId()));

        lobby.leave(command.playerId());

        lobbyRepository.save(lobby);

        return new LeaveLobbyUseCase.Result();
    }
}
