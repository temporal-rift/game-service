package io.github.temporalrift.game.action.application.saga;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.action.EventsDrawn;
import io.github.temporalrift.events.action.HandDealt;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

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
        var existing = playerStateRepository.findByGameIdAndPlayerId(event.gameId(), event.playerId());
        var state = existing.orElseGet(() -> new PlayerState(UUID.randomUUID(), event.gameId(), event.playerId()));
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
        if (event.faction() == null) {
            log.warn(
                    "Null faction for player {} in game {} — skipping action state projection",
                    event.playerId(),
                    event.gameId());
            return;
        }
        Faction faction;
        try {
            faction = Faction.valueOf(event.faction());
        } catch (IllegalArgumentException _) {
            log.warn(
                    "Unknown faction '{}' for player {} in game {} — skipping action state projection",
                    event.faction(),
                    event.playerId(),
                    event.gameId());
            return;
        }
        var existing = playerStateRepository.findByGameIdAndPlayerId(event.gameId(), event.playerId());
        if (existing.isPresent() && existing.get().faction() == faction) {
            return;
        }
        if (existing.isPresent()
                && existing.get().faction() != null
                && existing.get().faction() != faction) {
            throw new IllegalStateException("Conflicting faction assignment for player " + event.playerId());
        }
        var state = existing.orElseGet(() -> new PlayerState(UUID.randomUUID(), event.gameId(), event.playerId()));
        playerStateRepository.save(PlayerState.reconstitute(
                state.id(), state.gameId(), state.playerId(), faction, List.copyOf(state.hand()), state.isJammed()));
    }
}
