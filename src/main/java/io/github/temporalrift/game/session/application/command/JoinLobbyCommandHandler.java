package io.github.temporalrift.game.session.application.command;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.PlayerJoinedLobby;
import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@Service
class JoinLobbyCommandHandler implements JoinLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    private final SessionEventPublisher eventPublisher;

    JoinLobbyCommandHandler(LobbyRepository lobbyRepository, SessionEventPublisher eventPublisher) {
        this.lobbyRepository = lobbyRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        var lobby = lobbyRepository
                .findById(command.lobbyId())
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + command.lobbyId()));

        var player = new LobbyPlayer(command.playerId(), command.playerName(), null, null, true);
        lobby.join(player);

        lobbyRepository.save(lobby);

        eventPublisher.publish(EventEnvelope.create(
                lobby.id(),
                Lobby.AGGREGATE_TYPE,
                lobby.gameId(),
                1,
                new PlayerJoinedLobby(lobby.id(), command.playerId(), command.playerName())));

        var players = lobby.currentPlayers().stream()
                .map(p -> new PlayerSummary(
                        p.playerId(), p.playerName(), p.playerId().equals(lobby.hostPlayerId())))
                .toList();
        return new Result(lobby.id(), command.playerId(), players);
    }
}
