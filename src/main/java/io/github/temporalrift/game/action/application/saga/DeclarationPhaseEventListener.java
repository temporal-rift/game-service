package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActivistMomentumEligibility;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.DeclarationOptionsOffered;
import io.github.temporalrift.game.action.domain.event.DeclarationWindowOpened;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;
import io.github.temporalrift.game.shared.domain.event.HandSelectionCompleted;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

/**
 * Opens the explicit timed declaration window once every player holds a terminal final hand, publishing its public
 * expiry and each eligible participant's private offer in the same transaction that first creates the phase.
 */
@Component
class DeclarationPhaseEventListener {

    private final DeclarationPhaseRepository repository;
    private final DeclarationPhaseTimerScheduler timerScheduler;
    private final PlayerStateRepository playerStateRepository;
    private final ActivistMomentumEligibility momentumEligibility;
    private final ActionEventPublisher actionEventPublisher;
    private final GameRulesPort gameRules;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    DeclarationPhaseEventListener(
            DeclarationPhaseRepository repository,
            DeclarationPhaseTimerScheduler timerScheduler,
            PlayerStateRepository playerStateRepository,
            ActivistMomentumEligibility momentumEligibility,
            ActionEventPublisher actionEventPublisher,
            GameRulesPort gameRules,
            Clock clock,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.timerScheduler = timerScheduler;
        this.playerStateRepository = playerStateRepository;
        this.momentumEligibility = momentumEligibility;
        this.actionEventPublisher = actionEventPublisher;
        this.gameRules = gameRules;
        this.clock = clock;
        this.events = events;
    }

    @ApplicationModuleListener
    @Transactional(propagation = REQUIRES_NEW)
    void onHandSelectionCompleted(HandSelectionCompleted event) {
        if (repository
                .findByGameIdAndEraNumber(event.gameId(), event.eraNumber())
                .isPresent()) {
            return;
        }
        var offers = declarationOffers(event);
        var expiresAt = clock.instant()
                .plusSeconds(gameRules.declarationTimerSeconds(event.playerIds().size()));
        var phase = new DeclarationPhase(
                UUID.randomUUID(), event.gameId(), event.eraNumber(), expiresAt, List.copyOf(offers.keySet()));
        if (!repository.createIfAbsent(phase)) {
            return;
        }
        publish(phase, new DeclarationWindowOpened(phase.gameId(), phase.eraNumber(), phase.expiresAt()));
        offers.forEach((playerId, modes) ->
                publish(phase, new DeclarationOptionsOffered(phase.gameId(), phase.eraNumber(), playerId, modes)));
        if (phase.closeIfComplete()) {
            repository.save(phase);
            events.publishEvent(new DeclarationPhaseClosed(phase.gameId(), phase.eraNumber()));
        } else {
            timerScheduler.scheduleAfterCommit(phase.id(), phase.expiresAt());
        }
    }

    private Map<UUID, List<ActivistDeclarationMode>> declarationOffers(HandSelectionCompleted event) {
        var offers = new LinkedHashMap<UUID, List<ActivistDeclarationMode>>();
        for (var playerId : event.playerIds()) {
            var participant = playerStateRepository
                    .findByGameIdAndPlayerId(event.gameId(), playerId)
                    .orElseThrow(() -> new IllegalStateException("Declaration participant state is not ready"));
            if (participant.faction() == null) {
                throw new IllegalStateException("Declaration participant faction is not ready");
            }
            var modes = participant.eligibleDeclarationModes(
                    momentumEligibility.isEligible(event.gameId(), event.eraNumber(), playerId));
            if (!modes.isEmpty()) {
                offers.put(playerId, modes);
            }
        }
        return offers;
    }

    private void publish(DeclarationPhase phase, ActionEventPayload payload) {
        actionEventPublisher.publish(DomainEventEnvelope.create(
                phase.id(),
                DeclarationPhase.AGGREGATE_TYPE,
                phase.gameId(),
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                payload,
                clock));
    }
}
