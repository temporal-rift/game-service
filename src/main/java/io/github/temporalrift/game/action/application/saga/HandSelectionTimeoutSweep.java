package io.github.temporalrift.game.action.application.saga;

import java.time.Clock;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.shared.domain.event.LogicalClockAdvanced;

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

    @ApplicationModuleListener
    void onLogicalClockAdvanced(LogicalClockAdvanced ignored) {
        sweep();
    }

    @Scheduled(fixedDelayString = "${game.timers.hand-selection-sweep-interval}")
    void sweep() {
        repository.findOpenDueIds(clock.instant()).forEach(timeoutProcessor::resolve);
    }
}
