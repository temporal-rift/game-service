package io.github.temporalrift.game.scoring.application.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.session.EventsDrawn;
import io.github.temporalrift.game.session.FactionAssigned;
import io.github.temporalrift.game.shared.Faction;

@Component
class ScoringContextProjectionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ScoringContextProjectionEventListener.class);

    private final EraScoringContextRepository contextRepository;

    ScoringContextProjectionEventListener(EraScoringContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    @ApplicationModuleListener
    void onFactionAssigned(FactionAssigned event) {
        var faction = Faction.tryParse(event.faction()).orElse(null);
        if (faction == null) {
            log.warn(
                    "Invalid faction '{}' for player {} in game {} — skipping scoring context projection",
                    event.faction(),
                    event.playerId(),
                    event.gameId());
            return;
        }
        contextRepository.upsertPlayerFaction(event.gameId(), event.playerId(), faction);
    }

    @ApplicationModuleListener
    void onEventsDrawn(EventsDrawn event) {
        contextRepository.upsertExpectedOutcomeCount(
                event.gameId(), event.eraNumber(), event.events().size());
    }
}
