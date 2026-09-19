package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.shared.domain.event.HandSelectionCompleted;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

/** Opens the explicit timed declaration window once every player holds a terminal final hand. */
@Component
class DeclarationPhaseEventListener {

    private final DeclarationPhaseRepository repository;
    private final DeclarationPhaseTimerScheduler timerScheduler;
    private final GameRulesPort gameRules;
    private final Clock clock;

    DeclarationPhaseEventListener(
            DeclarationPhaseRepository repository,
            DeclarationPhaseTimerScheduler timerScheduler,
            GameRulesPort gameRules,
            Clock clock) {
        this.repository = repository;
        this.timerScheduler = timerScheduler;
        this.gameRules = gameRules;
        this.clock = clock;
    }

    @ApplicationModuleListener
    @Transactional(propagation = REQUIRES_NEW)
    void onHandSelectionCompleted(HandSelectionCompleted event) {
        var expiresAt = clock.instant()
                .plusSeconds(gameRules.declarationTimerSeconds(event.playerIds().size()));
        var phase = new DeclarationPhase(UUID.randomUUID(), event.gameId(), event.eraNumber(), expiresAt);
        if (repository.createIfAbsent(phase)) {
            timerScheduler.scheduleAfterCommit(phase.id(), phase.expiresAt());
        }
    }
}
