package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;

/** Filtered public payload disclosed only after Action Round 2 closes. */
public record ExposeSignatureRevealed(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID activistPlayerId,
        UUID targetPlayerId,
        ProbabilityInfluenceSignature signature)
        implements ActionEventPayload {}
