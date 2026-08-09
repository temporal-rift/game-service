package io.github.temporalrift.game.scoring.domain.context;

import java.util.List;
import java.util.UUID;

/**
 * One `ParadoxCascaded` fact for a game. Applies to every player in the game (see
 * {@code EraScoreEvaluator}'s cross-faction pass), not just one. eraNumber is the era the underlying
 * cascade actually happened in, which may differ from the era of the scoring pass that consumes this fact.
 */
public record ParadoxCascadeScoringFact(
        UUID paradoxId, UUID affectedEventId, List<UUID> detonatedByPlayerIds, int eraNumber) {}
