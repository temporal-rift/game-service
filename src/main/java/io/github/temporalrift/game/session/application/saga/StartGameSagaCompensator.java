package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
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

    public void initRunning(UUID gameId, UUID lobbyId) {
        this.startGameSagaRepository.save(
                new StartGameSagaState(UUID.randomUUID(), gameId, lobbyId, StartGameSagaStatus.RUNNING, 0, List.of()));
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void compensate(UUID gameId, String reason) {
        startGameSagaRepository.findByGameId(gameId).ifPresent(state -> {
            startGameSagaRepository.save(state.withStatus(StartGameSagaStatus.COMPENSATING));

            var lobby = lobbyRepository
                    .findById(state.lobbyId())
                    .orElseThrow(() ->
                            new IllegalStateException(LOBBY_NOT_FOUND.formatted(state.lobbyId(), state.sagaId())));
            lobby.resetFactionAssignments();
            lobbyRepository.save(lobby);

            eventPublisher.publish(EventEnvelope.create(
                    lobby.id(),
                    Lobby.AGGREGATE_TYPE,
                    state.gameId(),
                    1,
                    new GameStartFailed(state.gameId(), state.lobbyId(), reason)));

            startGameSagaRepository.save(state.withStatus(StartGameSagaStatus.FAILED));
        });
    }
}
