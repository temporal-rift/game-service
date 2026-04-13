package io.github.temporalrift.game.session.application.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.LobbyCreated;
import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@Service
class CreateLobbyCommandHandler implements CreateLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    private final SessionEventPublisher eventPublisher;

    CreateLobbyCommandHandler(LobbyRepository lobbyRepository, SessionEventPublisher eventPublisher) {
        this.lobbyRepository = lobbyRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var joinCode = generateUniqueJoinCode();
        var host = new LobbyPlayer(command.playerId(), command.playerName(), true, null);
        var lobby = new Lobby(lobbyId, gameId, command.playerId(), joinCode, List.of(host));

        lobbyRepository.save(lobby);

        eventPublisher.publish(EventEnvelope.create(
                "session.LobbyCreated",
                lobbyId,
                "Lobby",
                gameId,
                1,
                new LobbyCreated(lobbyId, command.playerId(), Instant.now())));

        return new Result(lobbyId, command.playerId(), joinCode);
    }

    private String generateUniqueJoinCode() {
        while (true) {
            var joinCode = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase();
            if (lobbyRepository.findByJoinCode(joinCode).isEmpty()) {
                return joinCode;
            }
        }
    }
}
