package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;

/** Advances the era to Action Round 1 once the declaration window completes or expires. */
@Component
class DeclarationPhaseClosedListener {

    private final EraSagaRepository eraSagaRepository;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;
    private final ActionRoundRequests actionRoundRequests;

    DeclarationPhaseClosedListener(
            EraSagaRepository eraSagaRepository,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher,
            ActionRoundRequests actionRoundRequests) {
        this.eraSagaRepository = eraSagaRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.actionRoundRequests = actionRoundRequests;
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
                            actionRoundRequests.open(event.gameId(), event.eraNumber(), 1, advanced.playerIds()));
                });
    }
}
