package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.HandSelected;
import io.github.temporalrift.game.shared.StartActionRoundRequested;

/** Advances the era only after every player has a terminal five-card hand. */
@Component
class HandSelectionEventListener {

    private final EraSagaRepository eraSagaRepository;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;
    private final SessionEventPublisher eventPublisher;
    private final Clock clock;

    HandSelectionEventListener(
            EraSagaRepository eraSagaRepository,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher,
            SessionEventPublisher eventPublisher,
            Clock clock) {
        this.eraSagaRepository = eraSagaRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @ApplicationModuleListener
    @Transactional(propagation = REQUIRES_NEW)
    void onHandSelected(HandSelected event) {
        eraSagaRepository
                .findByGameIdWithLock(event.gameId())
                .filter(state -> state.eraNumber() == event.eraNumber())
                .filter(state -> state.status() == EraSagaStatus.WAITING_HAND_SELECTION)
                .filter(state -> state.playerIds().contains(event.playerId()))
                .filter(state -> !state.handSelectedPlayerIds().contains(event.playerId()))
                .map(state -> {
                    var selected = state.markHandSelected(event.playerId());
                    return selected.allHandsSelected() ? selected.withStatus(EraSagaStatus.WAITING_ROUND_1) : selected;
                })
                .ifPresent(selected -> {
                    eraSagaRepository.save(selected);
                    eventPublisher.publish(DomainEventEnvelope.create(
                            event.gameId(),
                            Game.AGGREGATE_TYPE,
                            event.gameId(),
                            DomainEventEnvelope.SCHEMA_VERSION_V1,
                            event,
                            clock));
                    if (selected.status() == EraSagaStatus.WAITING_ROUND_1) {
                        applicationEventPublisher.publishEvent(new StartActionRoundRequested(
                                event.gameId(), event.eraNumber(), 1, selected.playerIds()));
                    }
                });
    }
}
