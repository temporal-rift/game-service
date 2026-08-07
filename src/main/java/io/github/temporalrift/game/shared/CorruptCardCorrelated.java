package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Cross-module event: the action module's round-close correlation matched an Eraser's Corrupt to the
 * specific probability-shift card its target actually played that round. The scoring module records this
 * as a durable candidate fact; it does not award a score credit on its own (see
 * {@code scoring_context_corrupt_correlation} and {@code EraScoreEvaluator.eraserDecisions} — awarding
 * {@code CORRUPTED_OPPONENT_CARD} needs a confirmation from timeline-service that the inversion actually
 * took effect, which does not exist yet; tracked in temporal-rift/timeline-service#12 and #16). In-process
 * only — never published to Kafka, since no other service needs the raw correlation.
 */
public record CorruptCardCorrelated(
        UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID targetPlayerId, UUID cardInstanceId) {}
