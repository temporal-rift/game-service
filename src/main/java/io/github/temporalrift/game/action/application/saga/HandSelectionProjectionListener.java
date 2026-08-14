package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.shared.HandDealt;

@Component
class HandSelectionProjectionListener {
    private final HandSelectionRepository repository;
    private final HandSelectionTimerScheduler timerScheduler;

    HandSelectionProjectionListener(HandSelectionRepository repository, HandSelectionTimerScheduler timerScheduler) {
        this.repository = repository;
        this.timerScheduler = timerScheduler;
    }

    @ApplicationModuleListener
    @Transactional(propagation = REQUIRES_NEW)
    void onHandDealt(HandDealt event) {
        var selection = HandSelection.open(event);
        if (repository.createIfAbsent(selection)) {
            timerScheduler.scheduleAfterCommit(selection.id(), selection.selectionExpiresAt());
        }
    }
}
