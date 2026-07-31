package io.github.temporalrift.game.session.application.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.SessionActivistDeclarationRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;

@Component
class SessionActivistDeclarationProjectionListener {

    private final SessionActivistDeclarationRepository repository;

    SessionActivistDeclarationProjectionListener(SessionActivistDeclarationRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    void onActivistDeclarationRecorded(ActivistDeclarationRecorded declaration) {
        repository.saveIfAbsent(
                declaration.gameId(),
                declaration.eraNumber(),
                declaration.playerId(),
                declaration.targetEventId(),
                declaration.mode());
    }
}
