package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;

@Component
class ActionRoundSagaStateManager {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaStateManager.class);

    private final ActionRoundSagaRepository repository;

    ActionRoundSagaStateManager(ActionRoundSagaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    ActionRoundSagaState initWaiting(
            UUID sagaId, UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds, Instant timerExpiresAt) {
        var state = new ActionRoundSagaState(
                sagaId,
                gameId,
                eraNumber,
                roundNumber,
                ActionRoundSagaStatus.WAITING,
                List.copyOf(playerIds),
                timerExpiresAt);
        return repository.save(state);
    }

    @Transactional
    Optional<ActionRoundSagaState> markSubmitted(UUID gameId, int eraNumber, int roundNumber, UUID playerId) {
        var stateOpt = repository.findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, eraNumber, roundNumber);
        if (stateOpt.isEmpty()) {
            log.warn("markSubmitted: saga not found for game {} era {} round {}", gameId, eraNumber, roundNumber);
            return Optional.empty();
        }
        var state = stateOpt.get();
        if (state.status() != ActionRoundSagaStatus.WAITING) {
            return Optional.of(state);
        }
        var updated = new ArrayList<>(state.pendingPlayerIds());
        updated.remove(playerId);
        return Optional.of(repository.save(state.withPendingPlayerIds(updated)));
    }

    @Transactional
    void markClosing(UUID gameId, int eraNumber, int roundNumber) {
        var stateOpt = repository.findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, eraNumber, roundNumber);
        if (stateOpt.isEmpty()) {
            return;
        }
        var state = stateOpt.get();
        if (state.status() == ActionRoundSagaStatus.COMPLETED || state.status() == ActionRoundSagaStatus.CLOSING) {
            return;
        }
        repository.save(state.withStatus(ActionRoundSagaStatus.CLOSING));
    }

    @Transactional
    void complete(UUID gameId, int eraNumber, int roundNumber) {
        var stateOpt = repository.findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber);
        if (stateOpt.isEmpty()) {
            return;
        }
        var state = stateOpt.get();
        if (state.status() == ActionRoundSagaStatus.COMPLETED) {
            return;
        }
        repository.save(state.withStatus(ActionRoundSagaStatus.COMPLETED));
    }

    Optional<ActionRoundSagaState> findBySagaId(UUID sagaId) {
        return repository.findBySagaId(sagaId);
    }

    List<ActionRoundSagaState> findWaitingDueBy(Instant deadline) {
        return repository.findWaitingDueBy(deadline);
    }

    List<ActionRoundSagaState> findAllClosing() {
        return repository.findAllClosing();
    }
}
