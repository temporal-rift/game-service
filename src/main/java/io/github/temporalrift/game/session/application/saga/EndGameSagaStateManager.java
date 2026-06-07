package io.github.temporalrift.game.session.application.saga;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.port.out.EndGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaState;
import io.github.temporalrift.game.session.domain.saga.EndGameSagaStatus;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;

@Component
class EndGameSagaStateManager {

    private final EndGameSagaRepository endGameSagaRepository;

    EndGameSagaStateManager(EndGameSagaRepository endGameSagaRepository) {
        this.endGameSagaRepository = endGameSagaRepository;
    }

    @Transactional
    void initRunning(UUID gameId, EndGameTrigger triggerType, List<UUID> playerIds) {
        endGameSagaRepository.save(new EndGameSagaState(gameId, triggerType, EndGameSagaStatus.RUNNING, playerIds));
    }

    @Transactional
    void complete(UUID gameId) {
        endGameSagaRepository
                .findByGameIdWithLock(gameId)
                .ifPresent(state -> endGameSagaRepository.save(state.withStatus(EndGameSagaStatus.COMPLETED)));
    }

    @Transactional
    void compensate(UUID gameId) {
        endGameSagaRepository
                .findByGameIdWithLock(gameId)
                .ifPresent(state -> endGameSagaRepository.save(state.withStatus(EndGameSagaStatus.COMPENSATING)));
    }
}
