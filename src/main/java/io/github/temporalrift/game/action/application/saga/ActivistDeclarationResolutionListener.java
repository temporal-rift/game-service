package io.github.temporalrift.game.action.application.saga;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationResolved;

@Component
class ActivistDeclarationResolutionListener {

    private final ActivistEraStateRepository activistEraStateRepository;

    ActivistDeclarationResolutionListener(ActivistEraStateRepository activistEraStateRepository) {
        this.activistEraStateRepository = activistEraStateRepository;
    }

    @ApplicationModuleListener
    void onActivistDeclarationResolved(ActivistDeclarationResolved event) {
        activistEraStateRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(event.gameId(), event.eraNumber(), event.playerId())
                .ifPresent(state -> {
                    state.recordResolution(event.succeeded());
                    activistEraStateRepository.save(state);
                });
    }
}
