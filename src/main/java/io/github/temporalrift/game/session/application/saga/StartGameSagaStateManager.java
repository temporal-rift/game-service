package io.github.temporalrift.game.session.application.saga;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

@Component
class StartGameSagaStateManager {

    private final StartGameSagaRepository startGameSagaRepository;

    StartGameSagaStateManager(StartGameSagaRepository startGameSagaRepository) {
        this.startGameSagaRepository = startGameSagaRepository;
    }

    @Transactional
    public void initRunning(UUID gameId, UUID lobbyId) {
        startGameSagaRepository.save(
                new StartGameSagaState(UUID.randomUUID(), gameId, lobbyId, StartGameSagaStatus.RUNNING, List.of()));
    }

    @Transactional
    public void complete(UUID gameId, UUID lobbyId) {
        startGameSagaRepository
                .findByGameIdWithLock(gameId)
                .ifPresent(state -> startGameSagaRepository.save(state.withStatus(StartGameSagaStatus.COMPLETED)));
    }
}
