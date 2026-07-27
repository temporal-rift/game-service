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
        // Pessimistic lock: save() rewrites the whole player collection, so concurrent leaves (or a
        // leave racing a join) must serialize or the last writer resurrects the other's removal.
        var lobby = lobbyRepository
                .findByIdWithLock(command.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(command.lobbyId()));

        lobby.leave(command.playerId());

        lobbyRepository.save(lobby);

        return new LeaveLobbyUseCase.Result();
    }
}
