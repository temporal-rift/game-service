package io.github.temporalrift.game.session.application.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Service
class JoinLobbyCommandHandler implements JoinLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    JoinLobbyCommandHandler(LobbyRepository lobbyRepository) {
        this.lobbyRepository = lobbyRepository;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var lobby = lobbyRepository
                .findById(command.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(command.lobbyId()));

        var player = new LobbyPlayer(command.playerId(), command.playerName(), null, null, true);
        lobby.join(player);

        lobbyRepository.save(lobby);

        var players = lobby.currentPlayers().stream()
                .map(p -> new PlayerSummary(
                        p.playerId(), p.playerName(), p.playerId().equals(lobby.hostPlayerId())))
                .toList();
        return new Result(lobby.id(), command.playerId(), players);
    }
}
