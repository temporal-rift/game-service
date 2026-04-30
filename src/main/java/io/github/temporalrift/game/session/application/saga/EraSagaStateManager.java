package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@Component
class EraSagaStateManager {

    private final EraSagaRepository eraSagaRepository;

    EraSagaStateManager(EraSagaRepository eraSagaRepository) {
        this.eraSagaRepository = eraSagaRepository;
    }

    @Transactional(propagation = REQUIRES_NEW)
    void initRunning(UUID gameId, int eraNumber, List<UUID> playerIds) {
        eraSagaRepository.save(new EraSagaState(gameId, eraNumber, EraSagaStatus.RUNNING, playerIds));
    }

    @Transactional(propagation = REQUIRES_NEW)
    void advanceTo(UUID gameId, EraSagaStatus status) {
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .ifPresent(state -> eraSagaRepository.save(state.withStatus(status)));
    }

    @Transactional(propagation = REQUIRES_NEW)
    void fail(UUID gameId) {
        eraSagaRepository
                .findByGameIdWithLock(gameId)
                .ifPresent(state -> eraSagaRepository.save(state.withStatus(EraSagaStatus.FAILED)));
    }
}
