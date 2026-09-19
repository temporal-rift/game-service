package io.github.temporalrift.game.action.application.saga;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;

@Component
class DeclarationPhaseTimeoutProcessor {

    private final DeclarationPhaseRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DeclarationPhaseTimeoutProcessor(
            DeclarationPhaseRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void resolve(UUID id) {
        repository.findByIdWithLock(id).ifPresent(phase -> {
            if (phase.closeIfOpen(clock.instant())) {
                repository.save(phase);
                events.publishEvent(new DeclarationPhaseClosed(phase.gameId(), phase.eraNumber()));
            }
        });
    }
}
