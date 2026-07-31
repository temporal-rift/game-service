package io.github.temporalrift.game.action.application.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationResolved;

@Component
class ActivistDeclarationResolutionListener {

    private static final Logger log = LoggerFactory.getLogger(ActivistDeclarationResolutionListener.class);

    private final ActivistEraStateRepository activistEraStateRepository;

    ActivistDeclarationResolutionListener(ActivistEraStateRepository activistEraStateRepository) {
        this.activistEraStateRepository = activistEraStateRepository;
    }

    @ApplicationModuleListener
    void onActivistDeclarationResolved(ActivistDeclarationResolved event) {
        activistEraStateRepository
                .findByGameIdAndEraNumberAndActivistPlayerId(event.gameId(), event.eraNumber(), event.playerId())
                .ifPresentOrElse(
                        state -> {
                            state.recordResolution(event.succeeded());
                            activistEraStateRepository.save(state);
                        },
                        () -> log.warn(
                                "No Activist era state for declaration resolution: game {}, era {}, player {}",
                                event.gameId(),
                                event.eraNumber(),
                                event.playerId()));
    }
}
