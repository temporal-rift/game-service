package io.github.temporalrift.game.scoring.application.listener;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.ExposeBehaviorChanged;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.ForesightDeclared;
import io.github.temporalrift.game.shared.OutcomeAnnihilated;

@Component
class ScoringContextProjectionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ScoringContextProjectionEventListener.class);

    private final EraScoringContextRepository contextRepository;
    private final EraScoringCompletionChecker completionChecker;
    private final ApplicationEventPublisher applicationEventPublisher;

    ScoringContextProjectionEventListener(
            EraScoringContextRepository contextRepository,
            EraScoringCompletionChecker completionChecker,
            ApplicationEventPublisher applicationEventPublisher) {
        this.contextRepository = contextRepository;
        this.completionChecker = completionChecker;
        this.applicationEventPublisher = applicationEventPublisher;
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
    void onActivistDeclarationRecorded(ActivistDeclarationRecorded event) {
        contextRepository.upsertActivistDeclaration(event);
        publishResolutions(event.gameId(), event.eraNumber());
        completionChecker.tryComplete(event.gameId(), event.eraNumber());
    }

    @ApplicationModuleListener
    void onExposeBehaviorChanged(ExposeBehaviorChanged event) {
        contextRepository.recordActionFact(
                event.gameId(),
                event.eraNumber(),
                event.activistPlayerId(),
                Faction.ACTIVISTS,
                io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason.EXPOSE_CHANGED_PLAYER_BEHAVIOR);
    }

    @ApplicationModuleListener
    void onEraActionFactsFinalized(EraActionFactsFinalized event) {
        // Redundant with onForesightDeclared/onOutcomeAnnihilated for this same round, but harmless:
        // both paths are idempotent upserts. This is the path that is *guaranteed* complete for the
        // final round, since it is built from the round's own submittedActions at close() time rather
        // than raced against independently-dispatched per-submission listeners.
        event.foresightFacts()
                .forEach(fact -> contextRepository.upsertWrittenOutcome(
                        event.gameId(), event.eraNumber(), fact.eventId(), fact.outcomeId(), fact.playerId()));
        event.annihilationFacts()
                .forEach(fact -> contextRepository.recordAnnihilatedOutcome(
                        event.gameId(), event.eraNumber(), fact.eventId(), fact.outcomeId(), fact.playerId()));
        event.exposeFacts()
                .forEach(fact -> contextRepository.recordActionFact(
                        event.gameId(),
                        event.eraNumber(),
                        fact.activistPlayerId(),
                        Faction.ACTIVISTS,
                        io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason
                                .EXPOSE_CHANGED_PLAYER_BEHAVIOR));
        event.activistDeclarationFacts()
                .forEach(fact -> contextRepository.upsertActivistDeclaration(new ActivistDeclarationRecorded(
                        event.gameId(),
                        event.eraNumber(),
                        1,
                        fact.playerId(),
                        fact.mode(),
                        fact.targetEventId(),
                        fact.targetOutcomeId())));
        event.revisionistFacts()
                .forEach(fact -> contextRepository.recordRevisionistAction(
                        event.gameId(),
                        event.eraNumber(),
                        fact.playerId(),
                        fact.action(),
                        fact.targetEventId(),
                        fact.targetOutcomeId()));
        contextRepository.resolveRevisionistActions(event.gameId(), event.eraNumber());
        contextRepository.markActionFactsReady(event.gameId(), event.eraNumber());
        publishResolutions(event.gameId(), event.eraNumber());
        completionChecker.tryComplete(event.gameId(), event.eraNumber());
    }

    private void publishResolutions(UUID gameId, int eraNumber) {
        contextRepository
                .resolveActivistDeclarations(gameId, eraNumber)
                .forEach(applicationEventPublisher::publishEvent);
    }
}
