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
        return repository
                .findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, eraNumber, roundNumber)
                .map(state -> removeFromPending(state, playerId))
                .or(() -> {
                    log.warn(
                            "markSubmitted: saga not found for game {} era {} round {}",
                            gameId,
                            eraNumber,
                            roundNumber);
                    return Optional.empty();
                });
    }

    private ActionRoundSagaState removeFromPending(ActionRoundSagaState state, UUID playerId) {
        if (state.status() != ActionRoundSagaStatus.WAITING) {
            return state;
        }
        var updated = new ArrayList<>(state.pendingPlayerIds());
        updated.remove(playerId);
        return repository.save(state.withPendingPlayerIds(updated));
    }

    @Transactional
    void markClosing(UUID gameId, int eraNumber, int roundNumber) {
        repository
                .findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, eraNumber, roundNumber)
                .filter(state -> state.status() != ActionRoundSagaStatus.COMPLETED
                        && state.status() != ActionRoundSagaStatus.CLOSING)
                .ifPresent(state -> repository.save(state.withStatus(ActionRoundSagaStatus.CLOSING)));
    }

    @Transactional
    void complete(UUID gameId, int eraNumber, int roundNumber) {
        repository
                .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber)
                .filter(state -> state.status() != ActionRoundSagaStatus.COMPLETED)
                .ifPresent(state -> repository.save(state.withStatus(ActionRoundSagaStatus.COMPLETED)));
    }

    Optional<ActionRoundSagaState> findBySagaId(UUID sagaId) {
        return repository.findBySagaId(sagaId);
    }

    List<ActionRoundSagaState> findWaitingDueBy(Instant deadline) {
        return repository.findWaitingDueBy(deadline);
    }
}
