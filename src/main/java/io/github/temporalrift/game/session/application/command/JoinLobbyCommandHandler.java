package io.github.temporalrift.game.session.application.command;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.shared.PlayerJoinedLobby;

@Service
class JoinLobbyCommandHandler implements JoinLobbyUseCase {

    private final LobbyRepository lobbyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    JoinLobbyCommandHandler(LobbyRepository lobbyRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.lobbyRepository = lobbyRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        // Pessimistic lock: concurrent joins must serialize so the max-players check cannot be
        // passed by two transactions at once.
        var lobby = lobbyRepository
                .findByIdWithLock(command.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(command.lobbyId()));

        lobby.join(command.playerId(), command.playerName());

        lobbyRepository.save(lobby);

        // In-process path for the scoring module's player-name projection (dual-publish pattern,
        // see developer-notes.md); the Kafka path is emitted by the lobby repository adapter.
        applicationEventPublisher.publishEvent(
                new PlayerJoinedLobby(lobby.gameId(), lobby.id(), command.playerId(), command.playerName()));

        var players = lobby.currentPlayers().stream()
                .map(p -> new PlayerSummary(
                        p.playerId(), p.playerName(), p.playerId().equals(lobby.hostPlayerId())))
                .toList();
        return new Result(lobby.id(), command.playerId(), players);
    }
}
