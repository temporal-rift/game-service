package io.github.temporalrift.game.scoring.domain.context;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EraScoringContext(
        UUID gameId,
        int eraNumber,
        List<PlayerFaction> players,
        List<EventOutcomeFact> eventOutcomes,
        List<ActionScoringFact> actionFacts,
        List<ChainScoringFact> chainFacts) {

    public EraScoringContext {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(players, "players must not be null");
        Objects.requireNonNull(eventOutcomes, "eventOutcomes must not be null");
        Objects.requireNonNull(actionFacts, "actionFacts must not be null");
        Objects.requireNonNull(chainFacts, "chainFacts must not be null");
        players = List.copyOf(players);
        eventOutcomes = List.copyOf(eventOutcomes);
        actionFacts = List.copyOf(actionFacts);
        chainFacts = List.copyOf(chainFacts);
    }
}
