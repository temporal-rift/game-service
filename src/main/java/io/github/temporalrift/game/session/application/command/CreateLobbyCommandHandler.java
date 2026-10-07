package io.github.temporalrift.game.session.application.command;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.ConnectionStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.JoinCodePort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.IdentityKind;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;

@Service
class CreateLobbyCommandHandler implements CreateLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    private final SessionGameRulesPort gameRules;

    private final JoinCodePort joinCodePort;

    private final ExecutionEntropy entropy;

    private final Clock clock;

    CreateLobbyCommandHandler(
            LobbyRepository lobbyRepository,
            SessionGameRulesPort gameRules,
            JoinCodePort joinCodePort,
            ExecutionEntropy entropy,
            Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.gameRules = gameRules;
        this.joinCodePort = joinCodePort;
        this.entropy = entropy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        var lobbyId = entropy.identity(IdentityKind.LOBBY, EntropyCoordinate.none());
        var gameId = entropy.identity(IdentityKind.GAME, EntropyCoordinate.none());
        var now = clock.instant();
        // No collision retry by design: a join-code clash in a ~6.6e11 space is rarer than any
        // other transaction failure, would only surface at commit-time flush (outside this method's
        // reach anyway), and an attacker cannot force one — the unique index is enough. An actual
        // collision falls through to GlobalExceptionHandler's unmapped-exception 500 like any other
        // unanticipated failure; it is not given any special "retryable" contract.
        var joinCode = joinCodePort.generate();
        var host = new LobbyPlayer(command.playerId(), command.playerName(), null, now, ConnectionStatus.CONNECTED);
        var config = new LobbyConfig(joinCode, gameRules.minPlayers(), gameRules.maxPlayers(), clock);
        var lobby = new Lobby(lobbyId, gameId, command.playerId(), List.of(host), config);

        lobbyRepository.save(lobby);

        return new Result(lobbyId, command.playerId(), joinCode);
    }
}
