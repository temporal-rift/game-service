package io.github.temporalrift.game.session.application.command;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.HostTransferred;
import io.github.temporalrift.events.session.LobbyClosed;
import io.github.temporalrift.events.session.PlayerLeftLobby;
import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.domain.lobby.LeaveOutcome;
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
    public void handle(Command command) {
        var lobby = lobbyRepository
                .findById(command.lobbyId())
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + command.lobbyId()));

        var outcome = lobby.leave(command.playerId());

        lobbyRepository.save(lobby);

        switch (outcome) {
            case LeaveOutcome.NonHostLeft() ->
                eventPublisher.publish(EventEnvelope.create(
                        lobby.id(),
                        Lobby.AGGREGATE_TYPE,
                        lobby.gameId(),
                        1,
                        new PlayerLeftLobby(lobby.id(), command.playerId())));

            case LeaveOutcome.HostTransferred(var newHostId) -> {
                eventPublisher.publish(EventEnvelope.create(
                        lobby.id(),
                        Lobby.AGGREGATE_TYPE,
                        lobby.gameId(),
                        1,
                        new HostTransferred(lobby.id(), command.playerId(), newHostId)));
                eventPublisher.publish(EventEnvelope.create(
                        lobby.id(),
                        Lobby.AGGREGATE_TYPE,
                        lobby.gameId(),
                        1,
                        new PlayerLeftLobby(lobby.id(), command.playerId())));
            }

            case LeaveOutcome.LobbyClosed() ->
                eventPublisher.publish(EventEnvelope.create(
                        lobby.id(),
                        Lobby.AGGREGATE_TYPE,
                        lobby.gameId(),
                        1,
                        new LobbyClosed(lobby.id(), lobby.gameId())));
        }
    }
}
