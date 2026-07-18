package io.github.temporalrift.game.action.application.saga;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.HandDealt;

@Component
class ActionStateProjectionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ActionStateProjectionEventListener.class);

    private final PlayerStateRepository playerStateRepository;
    private final FutureEventDefinitionPort futureEventDefinitionPort;

    ActionStateProjectionEventListener(
            PlayerStateRepository playerStateRepository, FutureEventDefinitionPort futureEventDefinitionPort) {
        this.playerStateRepository = playerStateRepository;
        this.futureEventDefinitionPort = futureEventDefinitionPort;
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
    void onHandDealt(HandDealt event) {
        // Find-or-create under lock: this listener and onFactionAssigned run in independent
        // post-commit transactions and both save the whole row, so an unlocked (or unguarded-create)
        // read-modify-write lets the last writer erase the other's field.
        var state = playerStateRepository.findOrCreateWithLock(event.gameId(), event.playerId());
        playerStateRepository.save(PlayerState.reconstitute(
                state.id(),
                state.gameId(),
                state.playerId(),
                state.faction(),
                event.cards().stream()
                        .map(card -> new PlayerState.CardInstance(card.cardInstanceId(), card.cardType()))
                        .toList(),
                state.isJammed()));
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
        playerStateRepository.save(PlayerState.reconstitute(
                state.id(), state.gameId(), state.playerId(), faction, List.copyOf(state.hand()), state.isJammed()));
    }
}
