package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;
import io.github.temporalrift.game.shared.domain.event.StartActionRoundRequested;

/** Advances the era to Action Round 1 once the declaration window closes on expiry. */
@Component
class DeclarationPhaseClosedListener {

    private final EraSagaRepository eraSagaRepository;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    DeclarationPhaseClosedListener(
            EraSagaRepository eraSagaRepository,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
        this.eraSagaRepository = eraSagaRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @ApplicationModuleListener
    @Transactional(propagation = REQUIRES_NEW)
    void onDeclarationPhaseClosed(DeclarationPhaseClosed event) {
        eraSagaRepository
                .findByGameIdWithLock(event.gameId())
                .filter(state -> state.eraNumber() == event.eraNumber())
                .filter(state -> state.status() == EraSagaStatus.WAITING_DECLARATION)
                .map(state -> state.withStatus(EraSagaStatus.WAITING_ROUND_1))
                .ifPresent(advanced -> {
                    eraSagaRepository.save(advanced);
                    applicationEventPublisher.publishEvent(
                            new StartActionRoundRequested(event.gameId(), event.eraNumber(), 1, advanced.playerIds()));
                });
    }
}
