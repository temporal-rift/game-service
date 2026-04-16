package io.github.temporalrift.game.session.application.command;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.PlayerLeftLobby;
import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@Service
class LeaveLobbyCommandHandler implements LeaveLobbyUseCase {

    private final LobbyRepository lobbyRepository;

    private final SessionEventPublisher eventPublisher;

    LeaveLobbyCommandHandler(LobbyRepository lobbyRepository, SessionEventPublisher eventPublisher) {
        this.lobbyRepository = lobbyRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void execute(Command command) {
        var lobby = lobbyRepository
                .findById(command.lobbyId())
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + command.lobbyId()));

        lobby.leave(command.playerId());

        lobbyRepository.save(lobby);

        eventPublisher.publish(EventEnvelope.create(
                lobby.id(),
                Lobby.AGGREGATE_TYPE,
                lobby.gameId(),
                1,
                new PlayerLeftLobby(lobby.id(), command.playerId())));
    }
}
