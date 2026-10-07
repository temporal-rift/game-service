package io.github.temporalrift.game.action.application.saga;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.shared.domain.model.EntropyCoordinate;
import io.github.temporalrift.game.shared.domain.model.EntropyPurpose;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionEntropy;

@Component
class HandSelectionTimeoutProcessor {
    private final HandSelectionRepository repository;
    private final ApplicationEventPublisher events;
    private final ExecutionEntropy entropy;
    private final Clock clock;

    HandSelectionTimeoutProcessor(
            HandSelectionRepository repository,
            ApplicationEventPublisher events,
            ExecutionEntropy entropy,
            Clock clock) {
        this.repository = repository;
        this.events = events;
        this.entropy = entropy;
        this.clock = clock;
    }

    @Transactional
    public void resolve(UUID id) {
        repository.findByIdWithLock(id).ifPresent(selection -> {
            var coordinate = EntropyCoordinate.none().era(selection.eraNumber()).player(selection.playerId());
            var random = entropy.generator(EntropyPurpose.HAND_TIMEOUT_SELECTION, coordinate);
            var resolved = selection.selectRandomOnExpiry(clock.instant(), random);
            if (resolved != selection) {
                repository.save(resolved);
                events.publishEvent(resolved.toEvent());
            }
        });
    }
}
