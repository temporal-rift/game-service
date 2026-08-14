package io.github.temporalrift.game.action.application.saga;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;

@Component
class HandSelectionTimeoutSweep {
    private final HandSelectionRepository repository;
    private final Clock clock;
    private final HandSelectionTimeoutProcessor timeoutProcessor;

    HandSelectionTimeoutSweep(
            HandSelectionRepository repository, Clock clock, HandSelectionTimeoutProcessor timeoutProcessor) {
        this.repository = repository;
        this.clock = clock;
        this.timeoutProcessor = timeoutProcessor;
    }

    @Scheduled(fixedDelayString = "${game.timers.hand-selection-sweep-interval}")
    void sweep() {
        repository.findOpenDueIds(clock.instant()).forEach(timeoutProcessor::resolve);
    }
}
