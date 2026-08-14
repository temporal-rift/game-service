package io.github.temporalrift.game.action.application.saga;

import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;

@Component
class HandSelectionTimeoutProcessor {
    private final HandSelectionRepository repository;
    private final ApplicationEventPublisher events;
    private final RandomGenerator random;
    private final Clock clock;

    HandSelectionTimeoutProcessor(
            HandSelectionRepository repository, ApplicationEventPublisher events, RandomGenerator random, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.random = random;
        this.clock = clock;
    }

    @Transactional
    public void resolve(UUID id) {
        repository.findByIdWithLock(id).ifPresent(selection -> {
            var resolved = selection.selectRandomOnExpiry(clock.instant(), random);
            if (resolved != selection) {
                repository.save(resolved);
                events.publishEvent(resolved.toEvent());
            }
        });
    }
}
