package io.github.temporalrift.game.action.application.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.application.ParadoxResolutionCardsOffering;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.event.EventsDrawn;
import io.github.temporalrift.game.shared.domain.event.FactionAssigned;
import io.github.temporalrift.game.shared.domain.event.HandSelected;
import io.github.temporalrift.game.shared.domain.model.Faction;

@Component
class ActionStateProjectionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ActionStateProjectionEventListener.class);

    private final PlayerStateRepository playerStateRepository;
    private final FutureEventDefinitionPort futureEventDefinitionPort;
    private final ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository;
    private final ParadoxResolutionCardsOffering paradoxResolutionCardsOffering;

    ActionStateProjectionEventListener(
            PlayerStateRepository playerStateRepository,
            FutureEventDefinitionPort futureEventDefinitionPort,
            ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository,
            ParadoxResolutionCardsOffering paradoxResolutionCardsOffering) {
        this.playerStateRepository = playerStateRepository;
        this.futureEventDefinitionPort = futureEventDefinitionPort;
        this.paradoxResolutionPhaseRepository = paradoxResolutionPhaseRepository;
        this.paradoxResolutionCardsOffering = paradoxResolutionCardsOffering;
    }

    @ApplicationModuleListener
    void onEventsDrawn(EventsDrawn event) {
        futureEventDefinitionPort.replaceForGameEra(
                event.gameId(),
                event.eraNumber(),
                event.events().stream()
                        .map(futureEvent -> new FutureEventDefinitionPort.EventDefinition(
                                futureEvent.eventId(),
                                futureEvent.outcomes().stream()
                                        .map(outcome -> new FutureEventDefinitionPort.OutcomeDefinition(
                                                outcome.outcomeId(), outcome.initialProbability()))
                                        .toList()))
                        .toList());
    }

    @ApplicationModuleListener
    void onHandSelected(HandSelected event) {
        // Find-or-create under lock: this listener and onFactionAssigned run in independent
        // post-commit transactions and both save the whole row, so an unlocked (or unguarded-create)
        // read-modify-write lets the last writer erase the other's field.
        var state = playerStateRepository.findOrCreateWithLock(event.gameId(), event.playerId());
        state.receiveHand(event.cards().stream()
                .map(card -> new PlayerState.CardInstance(card.cardInstanceId(), card.cardType(), card.grade()))
                .toList());
        playerStateRepository.save(state);
        adoptIntoOpenParadoxResolutionPhase(event, state);
    }

    /**
     * A hand projected after the era's paradox-resolution phase opened belongs to a participant the phase opening
     * could not see yet, so they are dealt their offer and eligible resolution cards now. For anyone already dealt,
     * the offering is a no-op.
     */
    private void adoptIntoOpenParadoxResolutionPhase(HandSelected event, PlayerState participant) {
        paradoxResolutionPhaseRepository
                .findByGameIdAndEraNumber(event.gameId(), event.eraNumber())
                .filter(phase -> phase.status() == ParadoxResolutionPhaseStatus.OPEN)
                .ifPresent(phase -> paradoxResolutionCardsOffering.offer(phase, participant));
    }

    @ApplicationModuleListener
    void onFactionAssigned(FactionAssigned event) {
        var faction = Faction.tryParse(event.faction()).orElse(null);
        if (faction == null) {
            log.warn(
                    "Invalid faction '{}' for player {} in game {} — skipping action state projection",
                    event.faction(),
                    event.playerId(),
                    event.gameId());
            return;
        }
        var state = playerStateRepository.findOrCreateWithLock(event.gameId(), event.playerId());
        if (state.faction() == faction) {
            return;
        }
        if (state.faction() != null) {
            throw new IllegalStateException("Conflicting faction assignment for player " + event.playerId());
        }
        state.assignFaction(faction);
        playerStateRepository.save(state);
    }
}
