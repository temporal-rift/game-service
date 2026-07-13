package io.github.temporalrift.game.scoring.application.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.action.EventsDrawn;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;

@Component
class ScoringContextProjectionEventListener {

    private final EraScoringContextRepository contextRepository;

    ScoringContextProjectionEventListener(EraScoringContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    @ApplicationModuleListener
    void onFactionAssigned(FactionAssigned event) {
        contextRepository.upsertPlayerFaction(event.gameId(), event.playerId(), Faction.valueOf(event.faction()));
    }

    @ApplicationModuleListener
    void onEventsDrawn(EventsDrawn event) {
        contextRepository.upsertExpectedOutcomeCount(
                event.gameId(), event.eraNumber(), event.events().size());
    }
}
