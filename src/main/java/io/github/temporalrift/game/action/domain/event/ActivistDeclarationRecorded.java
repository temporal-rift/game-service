package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;

/** Public, filtered action-contract payload for an Activist declaration of record. */
public record ActivistDeclarationRecorded(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID playerId,
        ActivistDeclarationMode mode,
        UUID targetEventId,
        UUID targetOutcomeId)
        implements ActionEventPayload {}
