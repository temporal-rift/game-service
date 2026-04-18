package io.github.temporalrift.game.session.application.command;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.LobbyCreated;
import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.JoinCodePort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@Service
class CreateLobbyCommandHandler implements CreateLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    private final SessionEventPublisher eventPublisher;

    private final GameRulesPort gameRules;

    private final JoinCodePort joinCodePort;

    private final Clock clock;

    CreateLobbyCommandHandler(
            LobbyRepository lobbyRepository,
            SessionEventPublisher eventPublisher,
            GameRulesPort gameRules,
            JoinCodePort joinCodePort,
            Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.eventPublisher = eventPublisher;
        this.gameRules = gameRules;
        this.joinCodePort = joinCodePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var now = clock.instant();
        var joinCode = joinCodePort.generate();
        var host = new LobbyPlayer(command.playerId(), command.playerName(), null, now, true);
        var lobby = new Lobby(
                lobbyId,
                gameId,
                command.playerId(),
                joinCode,
                List.of(host),
                gameRules.minPlayers(),
                gameRules.maxPlayers(),
                clock);

        lobbyRepository.save(lobby);

        eventPublisher.publish(EventEnvelope.create(
                lobbyId, Lobby.AGGREGATE_TYPE, gameId, 1, new LobbyCreated(lobbyId, command.playerId(), now)));

        return new Result(lobbyId, command.playerId(), joinCode);
    }
}
