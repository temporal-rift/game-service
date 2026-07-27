package io.github.temporalrift.game.scoring.application.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.ForesightDeclared;
import io.github.temporalrift.game.shared.OutcomeAnnihilated;

@Component
class ScoringContextProjectionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ScoringContextProjectionEventListener.class);

    // The era saga hard-caps rounds at 3 (see EraSagaAdvancer.FINAL_ROUND) — this is the round whose
    // ActionRoundClosed signals that no more action-module scoring facts can arrive for the era.
    private static final int FINAL_ROUND_NUMBER = 3;

    private final EraScoringContextRepository contextRepository;
    private final EraScoringCompletionChecker completionChecker;

    ScoringContextProjectionEventListener(
            EraScoringContextRepository contextRepository, EraScoringCompletionChecker completionChecker) {
        this.contextRepository = contextRepository;
        this.completionChecker = completionChecker;
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
        event.events()
                .forEach(futureEvent -> contextRepository.upsertEventOutcomeBaseline(
                        event.gameId(),
                        event.eraNumber(),
                        futureEvent.eventId(),
                        futureEvent.outcomes().size()));
    }

    @ApplicationModuleListener
    void onForesightDeclared(ForesightDeclared event) {
        contextRepository.upsertWrittenOutcome(
                event.gameId(), event.eraNumber(), event.eventId(), event.outcomeId(), event.playerId());
    }

    @ApplicationModuleListener
    void onOutcomeAnnihilated(OutcomeAnnihilated event) {
        contextRepository.recordAnnihilatedOutcome(
                event.gameId(), event.eraNumber(), event.eventId(), event.outcomeId(), event.playerId());
    }

    @ApplicationModuleListener
    void onActionRoundClosed(ActionRoundClosed event) {
        if (event.roundNumber() != FINAL_ROUND_NUMBER) {
            return;
        }
        contextRepository.markActionFactsReady(event.gameId(), event.eraNumber());
        completionChecker.tryComplete(event.gameId(), event.eraNumber());
    }
}
