package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.application.ActivistMomentumEligibility;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.DeclarationOptionsOffered;
import io.github.temporalrift.game.action.domain.event.DeclarationWindowOpened;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
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

    DeclarationPhaseEventListener(
            DeclarationPhaseRepository repository,
            DeclarationPhaseTimerScheduler timerScheduler,
            PlayerStateRepository playerStateRepository,
            ActivistMomentumEligibility momentumEligibility,
            ActionEventPublisher actionEventPublisher,
            GameRulesPort gameRules,
            Clock clock) {
        this.repository = repository;
        this.timerScheduler = timerScheduler;
        this.playerStateRepository = playerStateRepository;
        this.momentumEligibility = momentumEligibility;
        this.actionEventPublisher = actionEventPublisher;
        this.gameRules = gameRules;
        this.clock = clock;
    }

    @ApplicationModuleListener
    @Transactional(propagation = REQUIRES_NEW)
    void onHandSelectionCompleted(HandSelectionCompleted event) {
        var expiresAt = clock.instant()
                .plusSeconds(gameRules.declarationTimerSeconds(event.playerIds().size()));
        var phase = new DeclarationPhase(UUID.randomUUID(), event.gameId(), event.eraNumber(), expiresAt);
        if (!repository.createIfAbsent(phase)) {
            return;
        }
        publish(phase, new DeclarationWindowOpened(phase.gameId(), phase.eraNumber(), phase.expiresAt()));
        event.playerIds().forEach(playerId -> offerDeclarationOptions(phase, playerId));
        timerScheduler.scheduleAfterCommit(phase.id(), phase.expiresAt());
    }

    private void offerDeclarationOptions(DeclarationPhase phase, UUID playerId) {
        playerStateRepository
                .findByGameIdAndPlayerId(phase.gameId(), playerId)
                .map(participant -> participant.eligibleDeclarationModes(
                        momentumEligibility.isEligible(phase.gameId(), phase.eraNumber(), playerId)))
                .filter(modes -> !modes.isEmpty())
                .ifPresent(modes -> publish(
                        phase, new DeclarationOptionsOffered(phase.gameId(), phase.eraNumber(), playerId, modes)));
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
