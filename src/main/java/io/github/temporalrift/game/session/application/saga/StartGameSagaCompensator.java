package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.GameStartCancelled;
import io.github.temporalrift.events.session.GameStartFailed;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

@Component
class StartGameSagaCompensator {

    static final String LOBBY_NOT_FOUND = "Lobby %s not found during compensation for saga %s";

    private final StartGameSagaRepository startGameSagaRepository;

    private final LobbyRepository lobbyRepository;

    private final SessionEventPublisher eventPublisher;

    StartGameSagaCompensator(
            StartGameSagaRepository startGameSagaRepository,
            LobbyRepository lobbyRepository,
            SessionEventPublisher eventPublisher) {
        this.startGameSagaRepository = startGameSagaRepository;
        this.lobbyRepository = lobbyRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void cancel(UUID gameId) {
        startGameSagaRepository
                .findByGameIdWithLock(gameId)
                .filter(state -> state.status() == StartGameSagaStatus.RUNNING)
                .ifPresent(state -> {
                    startGameSagaRepository.save(state.withStatus(StartGameSagaStatus.CANCELLED));
                    eventPublisher.publish(EventEnvelope.create(
                            state.lobbyId(),
                            Lobby.AGGREGATE_TYPE,
                            state.gameId(),
                            1,
                            new GameStartCancelled(state.gameId(), state.lobbyId())));
                });
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void compensate(UUID sagaId, UUID gameId, UUID lobbyId, String reason) {
        // The RUNNING row start() wrote never commits — its transaction rolls back after this method
        // returns — so there is nothing to read here. Write the terminal FAILED record directly from
        // the identifiers the caller already had, instead of looking up state that cannot be visible
        // to this (separate, REQUIRES_NEW) transaction.
        startGameSagaRepository.save(
                new StartGameSagaState(sagaId, gameId, lobbyId, StartGameSagaStatus.FAILED, List.of()));

        var lobby = lobbyRepository
                .findById(lobbyId)
                .orElseThrow(() -> new IllegalStateException(LOBBY_NOT_FOUND.formatted(lobbyId, sagaId)));
        lobby.resetFactionAssignments();
        lobbyRepository.save(lobby);

        eventPublisher.publish(EventEnvelope.create(
                lobby.id(), Lobby.AGGREGATE_TYPE, gameId, 1, new GameStartFailed(gameId, lobbyId, reason)));
    }
}
