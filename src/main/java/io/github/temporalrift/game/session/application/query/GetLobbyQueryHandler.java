package io.github.temporalrift.game.session.application.query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.GetLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyAccessDeniedException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Service
class GetLobbyQueryHandler implements GetLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    GetLobbyQueryHandler(LobbyRepository lobbyRepository) {
        this.lobbyRepository = lobbyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        var lobby = lobbyRepository
                .findById(query.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(query.lobbyId()));
        requireMember(lobby, query);
        var members = lobby.currentPlayers().stream()
                .map(player -> new MemberSummary(
                        player.playerId(),
                        player.playerName(),
                        player.playerId().equals(lobby.hostPlayerId())))
                .toList();
        return new Result(
                lobby.id(), lobby.gameId(), lobby.hostPlayerId(), query.callerPlayerId(), lobby.status(), members);
    }

    private void requireMember(Lobby lobby, Query query) {
        var isMember = lobby.currentPlayers().stream()
                .anyMatch(player -> player.playerId().equals(query.callerPlayerId()));
        if (!isMember) {
            throw new LobbyAccessDeniedException(query.lobbyId());
        }
    }
}
