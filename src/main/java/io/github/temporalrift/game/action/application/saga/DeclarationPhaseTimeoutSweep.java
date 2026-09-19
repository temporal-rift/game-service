package io.github.temporalrift.game.action.application.saga;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;

@Component
class DeclarationPhaseTimeoutSweep {

    private final DeclarationPhaseRepository repository;
    private final Clock clock;
    private final DeclarationPhaseTimeoutProcessor timeoutProcessor;

    DeclarationPhaseTimeoutSweep(
            DeclarationPhaseRepository repository, Clock clock, DeclarationPhaseTimeoutProcessor timeoutProcessor) {
        this.repository = repository;
        this.clock = clock;
        this.timeoutProcessor = timeoutProcessor;
    }

    @Scheduled(fixedDelayString = "${game.timers.declaration-sweep-interval:1s}")
    void sweep() {
        repository.findOpenDueIds(clock.instant()).forEach(timeoutProcessor::resolve);
    }
}
