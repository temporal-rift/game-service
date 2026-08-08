package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

/**
 * A candidate Eraser Corrupt: {@code corruptingPlayerId}'s Corrupt matched {@code targetPlayerId}'s
 * probability-shift card at round close. {@code tookEffect} is {@code null} until a resolution-time
 * confirmation arrives that the inversion actually applied rather than being voided by a Seal — see
 * {@code EraScoreEvaluator.eraserDecisions} and temporal-rift/timeline-service#12 / #16.
 */
public record CorruptCorrelationFact(
        UUID corruptingPlayerId,
        UUID targetPlayerId,
        UUID cardInstanceId,
        UUID targetEventId,
        UUID sourceOutcomeId,
        UUID targetOutcomeId,
        Boolean tookEffect) {}
